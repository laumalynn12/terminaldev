#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>

int main(int argc, char *argv[]) {
    printf("TerminalDev PRoot execution test: SUCCESS\n");
    printf("PID=%d UID=%d GID=%d\n", getpid(), getuid(), getgid());
    for (int i = 0; i < argc; i++) {
        printf("arg[%d]=%s\n", i, argv[i]);
    }
    fflush(stdout);
    return 0;
}
