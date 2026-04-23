package com.example.warden;

import baritone.api.BaritoneAPI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WardenLogic {

    // Состояния бота
    private enum State { 
        IDLE, 
        WAITING_FOR_TELEPORT_HOME, 
        SEARCHING_CHEST, 
        FILLING_CLAN_STORAGE, 
        WAITING_FOR_TELEPORT_CLAN, 
        UNLOADING_CLAN_STORAGE 
    }

    private static State currentState = State.IDLE;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static int itemsInStorage = 0;
    private static final int MAX_STORAGE_SLOTS = 15;

    public static void init() {
        // Регистрация тика (20 раз в секунду)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Проверка на смерть: если умерли — возвращаемся в начало цикла
            if (!client.player.isAlive() && currentState != State.WAITING_FOR_TELEPORT_HOME) {
                startCycle();
            }

            onTick(client);
        });
    }

    private static void onTick(MinecraftClient client) {
        if (currentState == State.IDLE) {
            startCycle(); // Запуск при первом включении
        }

        if (currentState == State.SEARCHING_CHEST) {
            runBaritoneSearch();
        }
    }

    // --- Шаг 1: Телепортация домой ---
    public static void startCycle() {
        if (currentState == State.WAITING_FOR_TELEPORT_HOME) return;
        
        currentState = State.WAITING_FOR_TELEPORT_HOME;
        sendServerCommand("home home");

        // Ждем 8 секунд (серверная задержка)
        scheduler.schedule(() -> {
            currentState = State.SEARCHING_CHEST;
        }, 8, TimeUnit.SECONDS);
    }

    // --- Шаг 2: Поиск сундука через Baritone ---
    private static void runBaritoneSearch() {
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (!baritone.getPathingBehavior().isPathing()) {
            // Отправляем команду Baritone для поиска ближайшего сундука
            MinecraftClient.getInstance().player.networkHandler.sendCommand("goto chest");
        }
    }

    // --- Шаг 3 & 4: Обработка контейнеров (Интерфейсов) ---
    public static void handleScreenOpened(GenericContainerScreen screen) {
        int containerSize = screen.getScreenHandler().getInventory().size();

        if (currentState == State.SEARCHING_CHEST) {
            // Мы подошли к сундуку и открыли его
            lootChest(containerSize);
            
            // После того как забрали, открываем клановое хранилище
            currentState = State.FILLING_CLAN_STORAGE;
            sendServerCommand("clan storage");
        } 
        else if (currentState == State.FILLING_CLAN_STORAGE) {
            // Мы в /clan storage, перекладываем вещи
            fillClanStorage(containerSize);
            
            if (itemsInStorage >= MAX_STORAGE_SLOTS) {
                goToClanHome();
            } else {
                // Если место еще есть, идем искать следующий сундук
                currentState = State.SEARCHING_CHEST;
            }
        }
        else if (currentState == State.UNLOADING_CLAN_STORAGE) {
            // Мы в клан-холле, выгружаем /clan storage в физический сундук
            transferToFinalChest(containerSize);
        }
    }

    // --- Шаг 5: Переезд в Клан Холл ---
    private static void goToClanHome() {
        currentState = State.WAITING_FOR_TELEPORT_CLAN;
        sendServerCommand("clan home");

        scheduler.schedule(() -> {
            currentState = State.UNLOADING_CLAN_STORAGE;
            sendServerCommand("clan storage");
        }, 8, TimeUnit.SECONDS);
    }

    // --- Инструментарий работы с инвентарем ---

    private static void lootChest(int size) {
        var client = MinecraftClient.getInstance();
        for (int i = 0; i < size; i++) {
            quickMoveSlot(i);
        }
    }

    private static void fillClanStorage(int size) {
        // Перекладываем из инвентаря игрока в хранилище (15 слотов)
        for (int i = size; i < size + 36; i++) {
            if (itemsInStorage >= MAX_STORAGE_SLOTS) break;
            
            if (!getStackInSlot(i).isEmpty()) {
                quickMoveSlot(i);
                itemsInStorage++;
            }
        }
    }

    private static void transferToFinalChest(int size) {
        // 1. Забираем всё из clan storage
        lootChest(size);
        itemsInStorage = 0; // Сбрасываем счетчик
        
        // 2. После этого боту нужно открыть стоящий рядом сундук.
        // Здесь он просто вернется в цикл через 2 секунды после очистки.
        scheduler.schedule(WardenLogic::startCycle, 2, TimeUnit.SECONDS);
    }

    // Вспомогательные методы
    private static void sendServerCommand(String cmd) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.networkHandler.sendCommand(cmd);
        }
    }

    private static void quickMoveSlot(int slotId) {
        var client = MinecraftClient.getInstance();
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId, 
            slotId, 0, SlotActionType.QUICK_MOVE, client.player
        );
    }

    private static net.minecraft.item.ItemStack getStackInSlot(int id) {
        return MinecraftClient.getInstance().player.currentScreenHandler.getSlot(id).getStack();
    }
  }
