# CI build failure — captured 2026-09-26T14:27:37Z

Commit: 3da419db2bbc580977585c41d444b1205829c788
Run URL: https://github.com/yumedebug/aillm/actions/runs/36248441701

```
C/C++: /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
C/C++: /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
FAILURE: Build completed with 2 failures.
* What went wrong:
  /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
* What went wrong:
  /home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
/home/runner/work/aillm/aillm/diffusion/src/main/cpp/sd_bridge.cpp:72:39: error: cannot initialize a parameter of type 'JNIEnv **' (aka '_JNIEnv **') with an rvalue of type 'void **'
```
