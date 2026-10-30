package me.wesley1808.fastrtp.util;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public final class RegistryUtil {
    public static final TicketType LOCATE = registerTicketType("fastrtp:locate", 70L, TicketType.FLAG_LOADING);

    public static void register() {
    }

    private static TicketType registerTicketType(String id, long timeOut, int flag) {
        return Registry.register(BuiltInRegistries.TICKET_TYPE, id, new TicketType(timeOut, flag));
    }
}
