# Release build failure — captured 2026-09-26T14:29:04Z

Commit: 3da419db2bbc580977585c41d444b1205829c788
Run URL: https://github.com/yumedebug/aillm/actions/runs/36248452956

```
C/C++: /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
C/C++: /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
FAILURE: Build completed with 2 failures.
* What went wrong:
Execution failed for task ':diffusion:buildCMakeRelease[arm64-v8a]'.
  /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':diffusion:buildCMakeRelease[arm64-v8a]'.
Caused by: org.gradle.internal.UncheckedException: com.android.ide.common.process.ProcessException: ninja: Entering directory `/home/runner/work/aillm/aillm/diffusion/.cxx/Release/w411f6y6/arm64-v8a'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
Caused by: com.android.ide.common.process.ProcessException: ninja: Entering directory `/home/runner/work/aillm/aillm/diffusion/.cxx/Release/w411f6y6/arm64-v8a'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
Caused by: com.android.ide.common.process.ProcessException: Error while executing process /usr/local/lib/android/sdk/cmake/3.22.1/bin/ninja with arguments {-C /home/runner/work/aillm/aillm/diffusion/.cxx/Release/w411f6y6/arm64-v8a aillm_diffusion}
Caused by: org.gradle.process.ProcessExecutionException: Process 'command '/usr/local/lib/android/sdk/cmake/3.22.1/bin/ninja'' finished with non-zero exit value 1
* What went wrong:
Execution failed for task ':diffusion:buildCMakeRelease[x86_64]'.
  /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':diffusion:buildCMakeRelease[x86_64]'.
Caused by: org.gradle.internal.UncheckedException: com.android.ide.common.process.ProcessException: ninja: Entering directory `/home/runner/work/aillm/aillm/diffusion/.cxx/Release/w411f6y6/x86_64'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
Caused by: com.android.ide.common.process.ProcessException: ninja: Entering directory `/home/runner/work/aillm/aillm/diffusion/.cxx/Release/w411f6y6/x86_64'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
Caused by: com.android.ide.common.process.ProcessException: Error while executing process /usr/local/lib/android/sdk/cmake/3.22.1/bin/ninja with arguments {-C /home/runner/work/aillm/aillm/diffusion/.cxx/Release/w411f6y6/x86_64 aillm_diffusion}
Caused by: org.gradle.process.ProcessExecutionException: Process 'command '/usr/local/lib/android/sdk/cmake/3.22.1/bin/ninja'' finished with non-zero exit value 1
```
