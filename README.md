# Discord Bot Server Monitoring

A Discord bot for monitoring servers using **Prometheus Blackbox Exporter**.  
Built with **Spring Boot**, **JDA (Java Discord API)**, and **JFreeChart**.

---

## ✨ Features
- Periodic polling of Prometheus to check `probe_success`.
- Automatic alerts if a target has ≥ 5 timeouts per minute.
- **Global down session**: considered *DOWN* if at least one target is failing.
- Discord notifications:
  - During *downtime*: list of failing targets with error counts.
  - On *recovery*: all targets back up (with per-target outage charts).
- Cooldown mechanism to prevent alert spam.
