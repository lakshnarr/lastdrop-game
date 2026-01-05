# Last Drop – Expo Speech (2 pages)

## Page 1 – What it is

Hello, my name is Lakshna.

Today I want to show you my project called **Last Drop**.

It is a **board game that mixes real hardware and an Android app**.

My game has three main parts:

1) **A physical board** with lights and sensors.
2) **An Android phone app** that controls the game.
3) **A live screen on a web page** so other people can watch.

### The big idea

In the game, players travel across 20 tiles and learn about water.
Some tiles give points and some tiles take points.
Sometimes you land on a “chance” tile, and you get a surprise card.

### What you can see during the demo

When someone rolls the dice:

- We roll **GoDice**, which are smart Bluetooth dice.
- GoDice sends the dice number to the phone app.
- The phone app tells the board which tile to light up.
- The LEDs on the board light up so everyone can see the move.
- The board can sense when a player puts their coin on the correct tile.
- The phone app then updates the score and sends the newest game state to the live web screen.

So the same move is shown in three places:

- on the real board (lights),
- on the phone (game control),
- and on the spectator web screen (live view).

### How the physical board works (simple)

Inside the board is a small computer called an **ESP32**.

- It talks to the phone using **Bluetooth**, not Wi‑Fi.
- The phone also connects to the **GoDice** using Bluetooth.
- It controls a strip of **LED lights** to show where players are.
- Under each tile there are **magnet sensors**.
  The coins have magnets, so the board can tell when a coin is placed.

To keep the electronics stable, the board uses safe power wiring:

- A power bank provides power.
- A small power converter makes a steady power level for the ESP32.
- Extra capacitors help stop flickering and random resets.

## Page 2 – The AI and voice (Cloudie), and why it matters

### Cloudie: my friendly AI helper

My game also has an AI character called **Cloudie**.

Cloudie is like a friendly game host.
Cloudie can say things like:

- “It’s your turn, roll the dice.”
- “You landed on a chance tile.”
- “A coin is in the wrong place, please fix it.”
- “Congratulations, you won.”

Cloudie chooses lines based on what is happening in the game, like:

- the dice roll,
- which tile you landed on,
- whether there was a time‑out,
- or whether someone won.

### Voice: Cloudie can speak out loud

Cloudie can speak using a **voice system** in the Android app.

- If a premium voice is enabled, the app can generate a high‑quality spoken voice.
- If that is not available, the app can fall back to the phone’s built‑in text‑to‑speech voice.
- Voice can also be turned off in settings if we want silent mode.

### Cloudie can also be an AI player

Cloudie is not only a host.
Cloudie can also appear as a player profile in the app.

That means a person can play against Cloudie if they want.

### Why I built this

I built Last Drop because I like games, electronics, and coding.
I also wanted a fun way to talk about water and good choices.

This project is special because:

- It is not just a screen game.
  It uses a real board with lights and sensors.
- The phone app connects everything together.
- The live web screen lets other people watch the game in real time.
- The AI voice helper makes the game feel more alive.

Thank you for listening.

---

# One‑minute version (super short)

Hello, I’m Lakshna. This is Last Drop.
It is a board game that connects a real LED board and sensor coins to an Android app.
We roll **GoDice** (Bluetooth smart dice). The dice sends the number to the phone.
Then the phone tells the board which tile to light up using Bluetooth.
The board can detect when a magnet coin is placed on the correct tile.
The phone updates the score and also sends the game to a live web screen so people can watch.
It also has an AI helper named Cloudie who can speak and guide players.

---

# Judge Q and A (easy answers)

1) **What did you build?**
I built a board game system: a real board, an Android app, and a live spectator web screen.

2) **How does the phone talk to the board?**
By Bluetooth. The phone is the controller, and it connects to both the ESP32 board and the GoDice.

3) **How does the board know where the coin is?**
There are magnet sensors under the tiles, and the coin has a magnet.

4) **What does the ESP32 do?**
It controls the LED lights and reads the sensors, and it sends messages back to the phone.

5) **What is Cloudie?**
Cloudie is an AI character that can talk like a game host, and can also be a player.

6) **How does Cloudie talk?**
The app can use a premium voice if enabled, and if not it uses the phone’s own text‑to‑speech voice.

7) **What happens after a dice roll?**
The phone calculates the move, tells the board which tile to light, waits for coin placement, updates scores, and then updates the live web screen.

8) **What was the hardest part?**
Connecting hardware, Bluetooth, game rules, and the live screen so they all agree.

9) **Why is this project useful?**
It makes learning feel like playing, and it shows how phones can control real hardware.

10) **What would you add next?**
More sounds, more animations, and more game events for Cloudie to talk about.
