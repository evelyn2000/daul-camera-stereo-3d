LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_STATIC_JAVA_LIBRARIES := EvelynStereoJniCommon EvelynCommonUtils

LOCAL_PACKAGE_NAME := testStereoCamera

LOCAL_JNI_SHARED_LIBRARIES := libqsstereoctrl_jni

# LOCAL_MODULE_PATH := $(JZS_PATH_SOURCES)/packages/apps/VideoPlayer

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))