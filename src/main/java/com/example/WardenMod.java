package com.example.warden;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WardenMod implements ClientModInitializer {
    public static final String MOD_ID = "warden-bot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Warden Bot для 1.21.4 успешно запущен!");
        // Запуск логики
        WardenLogic.init();
    }
}
