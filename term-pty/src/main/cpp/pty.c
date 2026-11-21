#include <jni.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <dirent.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "TerminalDevPty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int throw_exception(JNIEnv *env, const char *msg) {
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exClass != NULL) {
        (*env)->ThrowNew(env, exClass, msg);
    }
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_term_dev_pty_PtyBridge_openPtySubprocess(
        JNIEnv *env,
        jclass clazz,
        jstring cmdStr,
        jstring cwdStr,
        jobjectArray argsArray,
        jobjectArray envVarsArray,
        jintArray pidOutArray,
        jint rows,
        jint cols) {
    (void) clazz;

    int masterFd = posix_openpt(O_RDWR | O_CLOEXEC | O_NOCTTY);
    if (masterFd < 0) {
        return throw_exception(env, "Failed to open PTY master (/dev/ptmx)");
    }

    if (grantpt(masterFd) != 0 || unlockpt(masterFd) != 0) {
        close(masterFd);
        return throw_exception(env, "Failed to grantpt/unlockpt on PTY master");
    }

    char slavePath[64];
    if (ptsname_r(masterFd, slavePath, sizeof(slavePath)) != 0) {
        close(masterFd);
        return throw_exception(env, "Failed to obtain slave PTY device path");
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ioctl(masterFd, TIOCSWINSZ, &ws);

    struct termios tios;
    if (tcgetattr(masterFd, &tios) == 0) {
        tios.c_iflag |= IUTF8;
        tios.c_iflag &= ~(IXON | IXOFF);
        tcsetattr(masterFd, TCSANOW, &tios);
    }

    const char *cmd = (*env)->GetStringUTFChars(env, cmdStr, NULL);
    const char *cwd = (*env)->GetStringUTFChars(env, cwdStr, NULL);

    jsize argCount = argsArray ? (*env)->GetArrayLength(env, argsArray) : 0;
    char **argv = (char **) malloc((argCount + 2) * sizeof(char *));
    argv[0] = strdup(cmd);
    for (jsize i = 0; i < argCount; i++) {
        jstring argStr = (jstring) (*env)->GetObjectArrayElement(env, argsArray, i);
        const char *arg = (*env)->GetStringUTFChars(env, argStr, NULL);
        argv[i + 1] = strdup(arg);
        (*env)->ReleaseStringUTFChars(env, argStr, arg);
        (*env)->DeleteLocalRef(env, argStr);
    }
    argv[argCount + 1] = NULL;

    jsize envCount = envVarsArray ? (*env)->GetArrayLength(env, envVarsArray) : 0;
    char **envp = (char **) malloc((envCount + 1) * sizeof(char *));
    for (jsize i = 0; i < envCount; i++) {
        jstring envStr = (jstring) (*env)->GetObjectArrayElement(env, envVarsArray, i);
        const char *var = (*env)->GetStringUTFChars(env, envStr, NULL);
        envp[i] = strdup(var);
        (*env)->ReleaseStringUTFChars(env, envStr, var);
        (*env)->DeleteLocalRef(env, envStr);
    }
    envp[envCount] = NULL;

    pid_t pid = fork();
    if (pid < 0) {
        close(masterFd);
        (*env)->ReleaseStringUTFChars(env, cmdStr, cmd);
        (*env)->ReleaseStringUTFChars(env, cwdStr, cwd);
        for (jsize i = 0; i <= argCount; i++) free(argv[i]);
        free(argv);
        for (jsize i = 0; i < envCount; i++) free(envp[i]);
        free(envp);
        return throw_exception(env, "fork() failed");
    }

    if (pid == 0) {
        setsid();
        int slaveFd = open(slavePath, O_RDWR);
        if (slaveFd < 0) {
            _exit(127);
        }
        ioctl(slaveFd, TIOCSCTTY, 0);

        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > STDERR_FILENO) {
            close(slaveFd);
        }
        close(masterFd);

        DIR *dir = opendir("/proc/self/fd");
        if (dir != NULL) {
            int dfd = dirfd(dir);
            struct dirent *de;
            while ((de = readdir(dir)) != NULL) {
                int fd = atoi(de->d_name);
                if (fd > STDERR_FILENO && fd != dfd) {
                    close(fd);
                }
            }
            closedir(dir);
        }

        if (cwd && cwd[0] != '\0') {
            chdir(cwd);
        }

        if (envp && envp[0] != NULL) {
            execve(cmd, argv, envp);
        } else {
            execvp(cmd, argv);
        }
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, cmdStr, cmd);
    (*env)->ReleaseStringUTFChars(env, cwdStr, cwd);
    for (jsize i = 0; i <= argCount; i++) free(argv[i]);
    free(argv);
    for (jsize i = 0; i < envCount; i++) free(envp[i]);
    free(envp);

    if (pidOutArray != NULL && (*env)->GetArrayLength(env, pidOutArray) > 0) {
        jint pidVal = (jint) pid;
        (*env)->SetIntArrayRegion(env, pidOutArray, 0, 1, &pidVal);
    }

    return masterFd;
}

JNIEXPORT void JNICALL
Java_com_term_dev_pty_PtyBridge_resizePty(
        JNIEnv *env,
        jclass clazz,
        jint fd,
        jint rows,
        jint cols) {
    (void) env;
    (void) clazz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_term_dev_pty_PtyBridge_waitForChild(
        JNIEnv *env,
        jclass clazz,
        jint pid) {
    (void) env;
    (void) clazz;
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    }
    return status;
}

JNIEXPORT void JNICALL
Java_com_term_dev_pty_PtyBridge_closeFd(
        JNIEnv *env,
        jclass clazz,
        jint fd) {
    (void) env;
    (void) clazz;
    if (fd >= 0) {
        close(fd);
    }
}
