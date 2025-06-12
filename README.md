# ProjectISC – MegaMix 🎵✨

![Scala](https://img.shields.io/badge/Scala-2.13-red?logo=scala)

> Codé 100 % Scala + libGDX2D, for the culture.
>
> *« If DDR et osu! avaient un enfant polyglotte en Scala… »* – Gontran

---

## 📝 Table des matières

* [TL;DR](#tldr)
* [Démo](#demo)
* [Gameplay](#gameplay)
* [Architecture](#architecture)
* [Build & Run](#build--run)
* [Licence](#licence)

---

## TL;DR

|                       |                                          |
| --------------------- | ---------------------------------------- |
| 🎹 **4 lanes**        | **W A S D**                              |
| ⬇️ **Auto‑DL**        | Midi files depuis `midis.triceratops.ch` |
| 🏆 **Leaderboard**    | scoring serveur                          |


<details>
<summary>Screenshot & GIF demo</summary>

*(Insère ici un GIF ou un screenshot `data/Assets/BackGround/background.png`)*

</details>

---

## Gameplay

| Touche | Lane       | Couleur  |
| ------ | ---------- | -------- |
| **W**  | 1 (haut)   | Vert 💚  |
| **A**  | 2 (gauche) | Jaune 💛 |
| **S**  | 3 (bas)    | Rouge ❤️ |
| **D**  | 0 (droite) | Bleu 💙  |

Le timing est roi :

| Feedback | Fenêtre | Score       |
| -------- | ------- | ----------- |
| Perfect  | ±50 ms  | 300×combo   |
| Good     | ±120 ms | 100×combo   |
| Miss     | >120 ms | Combo reset |

---

## Architecture

```mermaid
graph TD
    subgraph Desktop
        Launcher -->|start| RhythmGame
    end
    subgraph UI
        RhythmGame --> MainMenuScreen
        RhythmGame --> GameplayScreen
    end
    MainMenuScreen -->|fetch list JSON| RhythmApi
    MainMenuScreen -->|download .mid| MidiFiles
    GameplayScreen --> NoteLoader
    NoteLoader -->|parse| javax.sound.midi
```

* **gdx2d** : rendu 2D + portable window.
* **RhythmApi** : POST /register, /login, /score; GET /list, /leaderboard.
* **NoteLoader** : convertit ticks MIDI → positions circulaires « bullet‑hell ».
* **InstrumentExtractor** : Choix de l'instrument à l'utilisateur

---`

#### Dépendances Maven (extrait `build.sbt`)

```scala
libraryDependencies ++= Seq(
  "org.json4s" %% "json4s-jackson" % "4.0.7",
  "org.json4s" %% "json4s-native"  % "4.0.7",
  "com.badlogicgames.gdx" % "gdx-backend-lwjgl3" % "1.12.0"
)
```

## Licence

MIT

---

