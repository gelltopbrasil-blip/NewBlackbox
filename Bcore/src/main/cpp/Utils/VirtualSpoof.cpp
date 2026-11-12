#include <sys/system_properties.h>
#include <cstring>
#include <pthread.h>
#include "./xdl.h"
#include <android/log.h>
#include <dlfcn.h>
#include "Dobby/dobby.h"


#define LOG_TAG "VirtualSpoof"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Propriedades dinâmicas por instância: preenchidas pelo Java (NativeCore.setSystemProps)
// ao fazer o bind do app virtual, a partir do DeviceProfile do userId.
// Antes havia uma tabela fixa ("Pixel 6", release 12, ...) que contradizia Build.* e a versão real do Android.
#define MAX_PROPS 64
#define MAX_KEY 80

struct DynProp {
    char key[MAX_KEY];
    char value[PROP_VALUE_MAX];
};

static DynProp g_props[MAX_PROPS];
static int g_count = 0;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

// Valores fixos que não dependem da instância (flags de emulador sempre "0").
struct FixedProp {
    const char *key;
    const char *value;
};

static const FixedProp fixed_props[] = {
        {"ro.kernel.qemu",          "0"},
        {"ro.kernel.android.qemud", ""},
        {"ro.boot.qemu",            "0"},
        {nullptr, nullptr}
};

void spoof_set_prop(const char *key, const char *value) {
    if (!key || !value) return;
    pthread_mutex_lock(&g_lock);
    int idx = -1;
    for (int i = 0; i < g_count; ++i) {
        if (strcmp(g_props[i].key, key) == 0) { idx = i; break; }
    }
    if (idx < 0 && g_count < MAX_PROPS) idx = g_count++;
    if (idx >= 0) {
        strncpy(g_props[idx].key, key, MAX_KEY - 1);
        g_props[idx].key[MAX_KEY - 1] = '\0';
        strncpy(g_props[idx].value, value, PROP_VALUE_MAX - 1);
        g_props[idx].value[PROP_VALUE_MAX - 1] = '\0';
    }
    pthread_mutex_unlock(&g_lock);
}

static int (*orig_system_property_get)(const char *name, char *value) = nullptr;

int my_system_property_get(const char *name, char *value) {
    if (name && value) {
        pthread_mutex_lock(&g_lock);
        for (int i = 0; i < g_count; ++i) {
            if (strcmp(name, g_props[i].key) == 0) {
                strcpy(value, g_props[i].value);
                int len = (int) strlen(value);
                pthread_mutex_unlock(&g_lock);
                return len;
            }
        }
        pthread_mutex_unlock(&g_lock);

        for (int i = 0; fixed_props[i].key != nullptr; ++i) {
            if (strcmp(name, fixed_props[i].key) == 0) {
                strcpy(value, fixed_props[i].value);
                return (int) strlen(value);
            }
        }
    }
    if (orig_system_property_get) {
        return orig_system_property_get(name, value);
    }
    if (value) value[0] = '\0';
    return 0;
}

void install_property_get_hook() {
    void *handle = xdl_open("libc.so", XDL_DEFAULT);
    void *target = xdl_dsym(handle, "__system_property_get", nullptr);
    if (target) {
        if (DobbyHook(target, (void *) my_system_property_get, (void **) &orig_system_property_get) == 0) {
            LOGD("Spoof installed successfully");
        } else {
            LOGD("Spoof hook failed");
        }
        xdl_close(handle);
    } else {
        xdl_close(handle);
    }
}


__attribute__((constructor)) void init_virtual_spoof() {
    install_property_get_hook();
    LOGD("VirtualSpoof: __system_property_get hook loaded");
}
