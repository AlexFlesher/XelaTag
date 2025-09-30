// Source File
package me.alexflesher.xelaTag

// -------------------- Import Statements -------------------------
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.boss.BossBar
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemFlag
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.Sound
import java.util.UUID
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.GameMode
import org.bukkit.event.player.PlayerRespawnEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.ChatColor
// ---------------------------------------------------------------

class XelaTag : JavaPlugin(), Listener {

    // ----- GLOBAL REFERENCES -----
    var gameBorder: WorldBorder? = null
    var gameTimer: BukkitRunnable? = null
    var gameBossBar: BossBar? = null
    var gameActive: Boolean = false

    val runners = mutableListOf<Player>()
    val runnersAlive = mutableListOf<Player>()
    val hunters = mutableListOf<Player>()
    val huntersAlive = mutableListOf<Player>()
    val frozenPlayers = mutableSetOf<UUID>()
    var lastGameDurationSeconds: Int = 90
    val hunterTrackingIndex = mutableMapOf<Player, Int>()
    // -----------------------------

    // ----------------------------- Server OnEnable / OnDisable -------------------------------
    override fun onEnable() {
        server.pluginManager.registerEvents(this, this) // Register event listeners
        logger.info("XelaTag enabled!")

        // Register the tab completer for /xt
        getCommand("xt")?.tabCompleter = XelaTabCompleter()

        // Set World Border
        server.worlds[0].worldBorder.size = 30_000_000.0
        server.worlds[0].worldBorder.center = Location(server.worlds[0], 0.0, 0.0, 0.0)
    }

    override fun onDisable() {
        logger.info("XelaTag disabled!")
    }
    // ------------------------------------------------------------------------------------------

    // Join Message
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Teleport to the world's spawn
        player.teleport(Bukkit.getWorlds()[0].spawnLocation)

        // Set game mode to adventure
        player.gameMode = GameMode.ADVENTURE

        Bukkit.getScheduler().runTask(this, Runnable {
            player.sendMessage("")
            player.sendMessage("§8--------------------------------------")
            player.sendMessage("§6§lWelcome §b${player.name} §6to XelaTag!")
            player.sendMessage("§eUse §b/xt info §efor the command list")
            player.sendMessage("§8--------------------------------------")
            player.sendMessage("")
        })
    }

    // Give items to players once game starts
    fun giveGameItems(player: Player) {
        val inv = player.inventory
        inv.clear()

        inv.addItem(org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_AXE, 1))
        inv.addItem(org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_PICKAXE, 1))
        inv.addItem(org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SHOVEL, 1))
        inv.addItem(org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE, 16))

        if (hunters.contains(player)) {
            val compass = ItemStack(org.bukkit.Material.COMPASS, 1)
            val meta = compass.itemMeta
            meta?.setDisplayName("§bHunter's Tracker")
            meta?.addEnchant(Enchantment.UNBREAKING, 1, true) // Makes it glow
            meta?.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            meta?.isUnbreakable = true
            compass.itemMeta = meta
            inv.setItem(8, compass)
        }
    }

    fun preloadChunks(world: World, centerX: Int, centerZ: Int, radius: Int = 3) {
        // radius = how many chunks around the center to load
        for (cx in (centerX - radius)..(centerX + radius)) {
            for (cz in (centerZ - radius)..(centerZ + radius)) {
                world.loadChunk(cx, cz, true) // true = generate if not present
            }
        }
    }

    fun startCountdown(onFinish: () -> Unit) {
        var count = 3

        object : BukkitRunnable() {
            override fun run() {
                if (count > 0) {
                    for (p in runners + hunters) {
                        p.sendTitle("§e$count", "§7Get ready!", 0, 20, 0)
                        p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)
                    }
                    count--
                } else {
                    for (p in runners + hunters) {
                        p.sendTitle("§aGo!", "", 0, 20, 10)
                        p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                    }
                    frozenPlayers.clear() // release all
                    onFinish() // start the timer + announce game start
                    cancel()
                }
            }
        }.runTaskTimer(this, 0L, 20L) // run every second
    }

    // Start game timer when ./xt start is called
    fun startGameTimer(durationSeconds: Int) {
        var remainingTime = durationSeconds

        // Create BossBar at full progress
        gameBossBar = server.createBossBar("Timer: ${remainingTime}s", BarColor.GREEN, BarStyle.SOLID)

        // Add all online players to see it
        for (player in runners + hunters) {
            gameBossBar?.addPlayer(player)
        }

        gameTimer = object : BukkitRunnable() {
            override fun run() {
                if (remainingTime <= 0) {
                    gameBossBar?.setTitle("§aRunners Win! Time’s up!")
                    gameBossBar?.setProgress(0.0)
                    server.broadcastMessage("§d[XelaTag] §aRunners evaded the hunters in time! Runners win!")
                    stopGame("runners")
                    cancel()
                    return
                }

                // Update BossBar text + progress
                gameBossBar?.setTitle("Time Left: ${remainingTime}s")
                gameBossBar?.setProgress(remainingTime.toDouble() / durationSeconds.toDouble())

                // update color thresholds
                if (remainingTime <= 15) {
                    gameBossBar?.setColor(BarColor.RED);

                } else if (remainingTime <= durationSeconds / 2) {
                    gameBossBar?.setColor(BarColor.YELLOW);

                } else {
                    gameBossBar?.setColor(BarColor.GREEN);
                }

                for (hunter in hunters) {
                    if (!hunter.isOnline) continue
                    val nearestRunner = runnersAlive
                        .filter { it.isOnline }
                        .minByOrNull { it.location.distance(hunter.location) }

                    if (nearestRunner != null) {
                        hunter.compassTarget = nearestRunner.location
                    }
                }

                remainingTime--
            }
        }
        gameTimer?.runTaskTimer(this, 0L, 20L)
    }

    // Stop the game when:
    // 1. Time runs out - Runners Win!
    // 2. Hunters tag all runners - Hunters Win!
    // 3. ./xt stop is called
    fun stopGame(winningTeam: String? = null) {
        gameActive = false
        // Cancel the timer if it’s still running
        gameTimer?.cancel()
        gameTimer = null

        gameBossBar?.removeAll()
        gameBossBar = null

        // Remove the world border
        gameBorder?.let {
            it.size = 30_000_000.0
            it.center = Location(it.world!!, 0.0, 0.0, 0.0)
            gameBorder = null
        }

        val blank = TextComponent("") // blank line
        val playAgain = TextComponent("§d[XelaTag] §6Play Again? §l[Click Here]")
        playAgain.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xt start $lastGameDurationSeconds")

        // Clear all game members inventories
        for (player in runners + hunters) {
            player.inventory.clear()
            player.health = player.maxHealth // restore health
            player.foodLevel = 20           // restore hunger
            player.saturation = 20f         // max saturation\

            // If we know which team won, show title and play sound
            if (winningTeam != null) {
                val isWinner = when (winningTeam.lowercase()) {
                    "runners" -> runners.contains(player)
                    "hunters" -> hunters.contains(player)
                    else -> false
                }

                if (isWinner) {
                    player.sendTitle("§aYou Win!", "", 10, 70, 20)
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                } else {
                    player.sendTitle("§cYou Lose!", "", 10, 70, 20)
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                }
            }

            player.spigot().sendMessage(blank)
            player.spigot().sendMessage(playAgain)
        }

        // Clear runnersAlive if runners won
        runnersAlive.clear()
        huntersAlive.clear()

        // Delay teleport by 5 seconds (100 ticks)
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            if (!gameActive) {
                val worldSpawn = server.worlds[0].spawnLocation // or whichever world you want
                for (player in runners + hunters) {
                    if (player.isOnline) {

                        // Teleport to the world's spawn
                        player.teleport(Bukkit.getWorlds()[0].spawnLocation)

                        // Set game mode to adventure
                        player.gameMode = GameMode.ADVENTURE

                        player.teleport(worldSpawn)
                        player.sendMessage("§d[XelaTag] §aYou have been returned to spawn.")
                    }
                }
            }
        }, 100L) // 100 ticks = 5 seconds
    }

    @EventHandler
    fun onPlayerTagged(event: EntityDamageByEntityEvent) {
        // Only care about players damaging players
        if (event.entity !is Player || event.damager !is Player) return

        val victim = event.entity as Player
        val attacker = event.damager as Player

        // Only process if the game is running
        if (gameTimer == null) return

        // Check teams
        if (victim in runnersAlive && attacker in hunters) {
            // Remove tagged runner from the game
            runnersAlive.remove(victim)

            // Optionally, send a message to the tagged player
            victim.sendMessage("§d[XelaTag] §cYou were tagged by a hunter!")

            // Check if all runners are tagged
            if (runnersAlive.isEmpty()) {
                server.broadcastMessage("§d[XelaTag] §cHunters have tagged all runners! Hunters win!")
                stopGame("hunters")
            }
        }
    }

    @EventHandler
    fun onCompassUse(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        if (item.type != org.bukkit.Material.COMPASS) return
        if (!hunters.contains(player)) return

        // Only trigger on right click
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val runnersList = runnersAlive.filter { it.isOnline }
        if (runnersList.isEmpty()) {
            player.sendMessage("§d[XelaTag] §cNo runners left to track!")
            return
        }

        val isShiftClick = player.isSneaking

        // Get the current index for this hunter
        var index = hunterTrackingIndex.getOrDefault(player, 0)

        if (isShiftClick) {
            // Move to next runner in the list
            index = (index + 1) % runnersList.size
            hunterTrackingIndex[player] = index

            val newTarget = runnersList[index]
            player.compassTarget = newTarget.location
            player.sendMessage("§d[XelaTag] §eNow tracking §b${newTarget.name} §eat Y=${newTarget.location.blockY}")
        } else {
            // Normal right-click: just track the current runner
            val targetRunner = runnersList[index]
            player.compassTarget = targetRunner.location
            player.sendMessage("§d[XelaTag] §eTracking §b${targetRunner.name} §eat Y=${targetRunner.location.blockY}")
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (frozenPlayers.contains(player.uniqueId)) {
            // Cancel movement in X/Z, allow only looking
            if (event.from.x != event.to?.x || event.from.z != event.to?.z) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!gameActive) return // do nothing if no game is running

        val player = event.entity

        // Cancel default drops / XP if desired
        event.drops.clear()
        event.isCancelled = true // optional, keeps them from dropping items

        // Remove from alive lists
        runnersAlive.remove(player)
        huntersAlive.remove(player)

        // Check for win conditions
        if (runnersAlive.isEmpty()) {
            Bukkit.broadcastMessage("§d[XelaTag] §cAll runners are dead! Hunters win!")
            stopGame("hunters")
        } else if (huntersAlive.isEmpty()) {
            Bukkit.broadcastMessage("§d[XelaTag] §aAll hunters are dead! Runners win!")
            stopGame("runners")
        }

        // Mark player as dead: set gamemode to SPECTATOR immediately
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            player.gameMode = GameMode.SPECTATOR
        }, 1L) // 1 tick delay ensures Minecraft knows they actually died
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (!gameActive) return

        val player = event.player

        // Teleport to arena center or spectator area
        val arenaCenter = gameBorder?.center ?: Bukkit.getWorlds()[0].spawnLocation
        event.respawnLocation = arenaCenter

        // Set gamemode to SPECTATOR again just in case
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            player.gameMode = GameMode.SPECTATOR
            player.sendMessage("§d[XelaTag] §cYou died! You are now out of the game.")
        }, 1L)
    }

    fun isOceanBiome(world: World, x: Int, z: Int): Boolean {
        val surfaceY = world.getHighestBlockYAt(x, z)
        val biome = world.getBiome(x, surfaceY, z) // 3D version

        return when (biome) {
            org.bukkit.block.Biome.OCEAN,
            org.bukkit.block.Biome.COLD_OCEAN,
            org.bukkit.block.Biome.FROZEN_OCEAN,
            org.bukkit.block.Biome.LUKEWARM_OCEAN,
            org.bukkit.block.Biome.DEEP_OCEAN,
            org.bukkit.block.Biome.DEEP_COLD_OCEAN,
            org.bukkit.block.Biome.DEEP_FROZEN_OCEAN,
            org.bukkit.block.Biome.DEEP_LUKEWARM_OCEAN,
            org.bukkit.block.Biome.WARM_OCEAN -> true
            else -> false
        }
    }

    // Handle /xt command
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("xt", ignoreCase = true)) {
            if (args.isEmpty() || args[0].equals("info", ignoreCase = true)) {
                sender.sendMessage("")
                sender.sendMessage("§8--------------------------------------")
                sender.sendMessage("§6§lXelaTag Commands")
                sender.sendMessage("§b/xt runner §7- Join the runner team")
                sender.sendMessage("§b/xt hunter §7- Join the hunter team")
                sender.sendMessage("§b/xt start §7- Begins the game")
                sender.sendMessage("§b/xt stop §7- Stops the current game")
                sender.sendMessage("§b/xt info §7- Displays this page")
                sender.sendMessage("§8--------------------------------------")
                sender.sendMessage("")
                return true
            }

            if (args[0].equals("runner", ignoreCase = true)) {
                if (sender is Player) {
                    val player = sender
                    if (!runners.contains(player)) {
                        runners.add(player)
                        // Remove from hunters if they were there
                        hunters.remove(player)
                        player.sendMessage("§d[XelaTag] §aYou have joined the §6Runner §ateam!")
                    } else {
                        player.sendMessage("§d[XelaTag] §cYou are already on the Runner team!")
                    }
                } else {
                    sender.sendMessage("§d[XelaTag] §cOnly players can join a team!")
                }
                return true
            }

            if (args[0].equals("hunter", ignoreCase = true)) {
                if (sender is Player) {
                    val player = sender
                    if (!hunters.contains(player)) {
                        hunters.add(player)
                        runners.remove(player)
                        player.sendMessage("§d[XelaTag] §aYou have joined the §cHunter §ateam!")
                    } else {
                        player.sendMessage("§d[XelaTag] §cYou are already on the Hunter team!")
                    }
                } else {
                    sender.sendMessage("§d[XelaTag] §cOnly players can join a team!")
                }
                return true
            }

            if (args[0].equals("start", ignoreCase = true)) {
                if (gameActive) {
                    sender.sendMessage("§d[XelaTag] §cCannot start game: Game already in progress.")
                    return true
                }

                if (runners.isEmpty() || hunters.isEmpty()) {
                    sender.sendMessage("§d[XelaTag] §cCannot start game: Both teams must have at least one player!")
                    return true
                }

                // Determine duration
                val durationSeconds = if (args.size > 1) {
                    try {
                        args[1].toInt().coerceAtLeast(1) // prevent 0 or negative
                    } catch (e: NumberFormatException) {
                        sender.sendMessage("§d[XelaTag] §cInvalid duration, using default 90 seconds.")
                        90
                    }
                } else {
                    90
                }

                lastGameDurationSeconds = durationSeconds
                Bukkit.broadcastMessage("§d[XelaTag] §eTime Set: §l$durationSeconds§r§es!")

                val world = server.worlds[0] // Main world, adjust if needed

                // Random center location for the game (not at 0,0)
                val random = java.util.Random()
                var centerX: Int
                var centerZ: Int

                do {
                    centerX = random.nextInt(20_001) - 10_000
                    centerZ = random.nextInt(20_001) - 10_000
                } while (isOceanBiome(world, centerX, centerZ))

                val centerY = world.getHighestBlockYAt(centerX, centerZ).toDouble()

                val borderSize = 100.0
                val halfBorder = borderSize / 2

                Bukkit.broadcastMessage("§d[XelaTag] §eGenerating region chunks, please wait...")

                preloadChunks(world, centerX / 16, centerZ / 16, radius = 4)

                // Wait 3 seconds (60 ticks) before teleporting players
                Bukkit.getScheduler().runTaskLater(this, Runnable {
                    // Teleport runners
                    for (player in runners) {
                        player.gameMode = GameMode.SURVIVAL
                        val spawnX = centerX - halfBorder + 3
                        val spawnZ = centerZ - halfBorder + 3
                        val spawnY = world.getHighestBlockYAt(spawnX.toInt(), spawnZ.toInt()).toDouble() + 1
                        player.teleport(org.bukkit.Location(world, spawnX, spawnY, spawnZ))
                        giveGameItems(player)
                        runnersAlive.add(player)
                    }

                    // Teleport hunters
                    for (player in hunters) {
                        player.gameMode = GameMode.SURVIVAL
                        val spawnX = centerX + halfBorder - 3
                        val spawnZ = centerZ + halfBorder - 3
                        val spawnY = world.getHighestBlockYAt(spawnX.toInt(), spawnZ.toInt()).toDouble() + 1
                        player.teleport(org.bukkit.Location(world, spawnX, spawnY, spawnZ))
                        giveGameItems(player)
                        huntersAlive.add(player)
                    }

                    // Set the world border
                    val border = world.worldBorder
                    border.center = org.bukkit.Location(world, centerX.toDouble(), centerY, centerZ.toDouble())
                    border.size = borderSize
                    gameBorder = border // Save border to global reference

                    // Freeze all players
                    for (p in runners + hunters) {
                        frozenPlayers.add(p.uniqueId)
                    }

                    // Start countdown and then timer
                    startCountdown {
                        Bukkit.broadcastMessage("§d[XelaTag] §aGame Started!")
                        startGameTimer(durationSeconds)
                    }

                    gameActive = true

                }, 60L) // 60 ticks = 3 seconds

                return true
            }

            if (args[0].equals("stop", ignoreCase = true)) {
                if (gameActive) {
                    server.broadcastMessage("§d[XelaTag] §cGame was stopped.")
                    stopGame()
                    return true
                } else {
                    server.broadcastMessage("§d[XelaTag] §cNo active game found.")
                    return true
                }
            }

            if (args[0].equals("reload", ignoreCase = true)) {

                // Clear or reinitialize plugin state
                runners.clear();
                runnersAlive.clear();
                hunters.clear();
                huntersAlive.clear();

                // Cancel the timer if it’s still running
                gameTimer?.cancel()
                gameTimer = null

                gameBossBar?.removeAll()
                gameBossBar = null

                // Remove the world border
                gameBorder?.let {
                    it.size = 30_000_000.0
                    it.center = Location(it.world!!, 0.0, 0.0, 0.0)
                    gameBorder = null
                }
                // Reinitialize any other state your plugin uses here

                // Notify the player
                sender.sendMessage("§aPlugin reloaded successfully!");
                return true
            }

            // Unknown Args
            sender.sendMessage("§d[XelaTag] §cUsage: /xt info")
            return true
        }
        return false
    }
}
