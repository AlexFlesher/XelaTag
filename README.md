# XelaTag – Minecraft Tag Minigame Plugin  

XelaTag is a fast-paced **tag-style minigame plugin** for Minecraft servers, written in **Kotlin** using the Spigot/Paper API. Players can compete as **Hunters** or **Runners** in timed rounds, with compass tracking, bossbar timers, and smooth game flow.  

---

## ✨ Features  
- ⚔️ **Team System** – Hunters vs Runners with automatic assignment  
- 🧭 **Compass Tracking** – Hunters track the nearest Runner  
- ⏱️ **Bossbar Timer** – Customizable round timers displayed to all players   
- 🔄 **Game Flow** – Start, stop, and replay rounds seamlessly  
- 📝 **Configurable Settings** – Customize gameplay values to fit your server  

---

## 🚀 Installation  
1. Download the latest release of **XelaTag.jar**  
2. Place it in your server’s `plugins/` folder  
3. Restart (or reload) your server  

---

## 🎮 Commands  
| Command | Description |
|---------|-------------|
| `/xt start` | Starts a new game (requires at least one Hunter and Runner) |
| `/xt stop`  | Stops the current game and resets players |
| `/xt hunter/runner` | Joins the specified team (hunter/runner) |
| `/xt info` | Displays commands |
| `/xt reload` | Reloads plugin configuration |

---

## ⚙️ Coming Soon: Configuration
The plugin generates a `config.yml` with settings like:  
- **Timer length** (round duration)  
- **Arena center & size**  
- **Spawn points**  
- **Messages & colors**  

---

## 🏆 Coming Soon: Points & Scoring  
A **points system** is in development that will allow servers to:  
- Track player scores across multiple games  
- Award points for tagging opponents or surviving as a Runner  
- Display a scoreboard during and after rounds  
- Optionally persist stats between restarts  

---

## 🛠️ Development  
- Built with **Kotlin**  
- Target: **Spigot/Paper 1.20+**  
- Dependencies: None (lightweight & standalone)  

### Building from source  
```bash
./gradlew build
