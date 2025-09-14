package id.my.agungdh.discordbotservermonitoring.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(OshiNativeHints.Registrar.class)
public class OshiNativeHints {
    static class Registrar implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader cl) {
            // Proxy untuk JNA yang dipakai OSHI di Linux
            try {
                hints.proxies().registerJdkProxy(
                        Class.forName("oshi.jna.platform.linux.LinuxLibc")
                );
            } catch (ClassNotFoundException e) {
                System.out.println(e.getMessage());
            }

            // Kalau suatu saat kamu ingin allow udev lagi:
            try {
                hints.proxies().registerJdkProxy(
                        Class.forName("com.sun.jna.platform.linux.Udev")
                );
            } catch (ClassNotFoundException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
