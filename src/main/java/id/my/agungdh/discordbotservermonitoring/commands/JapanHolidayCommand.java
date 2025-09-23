package id.my.agungdh.discordbotservermonitoring.commands;

import id.my.agungdh.discordbotservermonitoring.service.JapanHolidayService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class JapanHolidayCommand implements SlashCommand {

    private final JapanHolidayService holidayService;

    public JapanHolidayCommand(JapanHolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @Override
    public String name() {
        return "jp-holiday";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("Pilih subcommand: `today`, `month`, atau `all`.").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "today" -> handleToday(event);
            case "month" -> handleMonth(event);
            case "all" -> handleAll(event);
            default -> event.reply("Subcommand tidak dikenal.").setEphemeral(true).queue();
        }
    }

    private void handleToday(SlashCommandInteractionEvent event) {
        var todayJp = LocalDate.now(JapanHolidayService.JAPAN_ZONE);
        var opt = holidayService.getHoliday(todayJp);
        if (opt.isPresent()) {
            var h = opt.get();
            event.reply("🎌 **Hari ini libur di Jepang** (" + todayJp + "): **" + h.name() + "**")
                    .queue(); // 🔥 tanpa .setEphemeral
        } else {
            event.reply("📅 Hari ini (" + todayJp + ") **bukan** hari libur di Jepang.")
                    .queue(); // 🔥 tanpa .setEphemeral
        }
    }

    private void handleMonth(SlashCommandInteractionEvent event) {
        var ymJp = YearMonth.from(LocalDate.now(JapanHolidayService.JAPAN_ZONE));
        var list = holidayService.getHolidaysInMonth(ymJp);
        if (list.isEmpty()) {
            event.reply("📅 Bulan ini (" + ymJp + ") tidak ada libur di Jepang.")
                    .queue(); // 🔥
            return;
        }
        var sb = new StringBuilder("🎌 Libur Jepang bulan **" + ymJp + "**:\n");
        list.forEach(h -> sb.append("- `").append(h.date()).append("` — ").append(h.name()).append("\n"));
        event.reply(sb.toString()).queue(); // 🔥
    }

    private void handleAll(SlashCommandInteractionEvent event) {
        int year = LocalDate.now(JapanHolidayService.JAPAN_ZONE).getYear();
        var list = holidayService.getAllHolidays(year);
        if (list.isEmpty()) {
            event.reply("Belum ada data libur untuk tahun " + year + ".").queue(); // 🔥
            return;
        }
        var sb = new StringBuilder("🎌 **Daftar libur Jepang " + year + "**:\n");
        list.forEach(h -> sb.append("- `").append(h.date()).append("` — ").append(h.name()).append("\n"));
        event.reply(sb.toString()).queue(); // 🔥
    }
}
