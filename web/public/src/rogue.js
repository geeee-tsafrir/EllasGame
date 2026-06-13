const ROGUE_TILES = {
  WALL: "wall",
  FLOOR: "floor",
  DOOR: "door",
  EXIT: "exit"
};
const ROGUE_DIRECTIONS = {
  up: { x: 0, y: -1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
  down: { x: 0, y: 1 }
};
const ROGUE_MONSTER_TEMPLATES = [
  { name: "Guard", attack: 4, defense: 1, hp: 10, speed: 1 },
  { name: "Scout", attack: 3, defense: 1, hp: 8, speed: 2 },
  { name: "Brute", attack: 6, defense: 2, hp: 14, speed: 1 }
];
const ROGUE_VIEWPORT_SIZE = 10;
const ROGUE_VIEWPORT_PLAYER_OFFSET = 5;

export function createRogueController(deps) {
  const {
    DIRECTIONS,
    app,
    state,
    stopCamera,
    renderMenu,
    nextEntry,
    resolveDirection,
    replayButtonHtml,
    challengeDetailText,
    challengeDisplayText,
    compareArabic,
    compareHebrew,
    expectedFeedbackHtml,
    userFeedbackHtml,
    spokenDetail,
    speakText,
    escapeHtml,
    one,
    all
  } = deps;

  function startRogueGame() {
    stopCamera();
    state.attempts = [];
    state.rogue = createRogueGame(1);
    renderRogueDungeon();
  }

  function exitRogueToMenu() {
    renderMenu();
  }
  
  function createRogueGame(level) {
    const dungeon = generateRogueDungeon(level);
    return {
      level,
      tiles: dungeon.tiles,
      width: dungeon.width,
      height: dungeon.height,
      rooms: dungeon.rooms,
      player: {
        x: dungeon.start.x,
        y: dungeon.start.y,
        attack: 5 + Math.floor((level - 1) / 2),
        defense: 1 + Math.floor((level - 1) / 3),
        hp: 28 + (level - 1) * 4,
        maxHp: 28 + (level - 1) * 4,
        speed: 1
      },
      monsters: dungeon.monsters,
      explored: new Set(),
      message: "Find the door to the next level.",
      combat: null,
      doorChallenge: null,
      monsterAttack: null,
      turn: 0
    };
  }
  
  function generateRogueDungeon(level) {
    const width = 18;
    const height = 14;
    const tiles = Array.from({ length: height }, () => Array.from({ length: width }, () => ROGUE_TILES.WALL));
    const rooms = [];
    const roomCount = 5 + Math.min(3, level);
    for (let attempt = 0; attempt < 120 && rooms.length < roomCount; attempt++) {
      const room = {
        x: randomInt(1, width - 6),
        y: randomInt(1, height - 5),
        width: randomInt(4, 6),
        height: randomInt(3, 5)
      };
      if (rooms.some((existing) => roomsOverlap(room, existing))) {
        continue;
      }
      carveRoom(tiles, room);
      if (rooms.length > 0) {
        connectRooms(tiles, roomCenter(rooms[rooms.length - 1]), roomCenter(room));
      }
      rooms.push(room);
    }
    if (rooms.length === 0) {
      const fallback = { x: 2, y: 2, width: 8, height: 6 };
      carveRoom(tiles, fallback);
      rooms.push(fallback);
    }
    placeDoors(tiles);
    const start = roomCenter(rooms[0]);
    const exit = roomCenter(rooms[rooms.length - 1]);
    tiles[exit.y][exit.x] = ROGUE_TILES.EXIT;
    const monsters = placeMonsters(tiles, rooms.slice(1), level);
    return { tiles, width, height, rooms, start, monsters };
  }
  
  function roomsOverlap(first, second) {
    return first.x <= second.x + second.width
      && first.x + first.width >= second.x
      && first.y <= second.y + second.height
      && first.y + first.height >= second.y;
  }
  
  function carveRoom(tiles, room) {
    for (let y = room.y; y < room.y + room.height; y++) {
      for (let x = room.x; x < room.x + room.width; x++) {
        tiles[y][x] = ROGUE_TILES.FLOOR;
      }
    }
  }
  
  function connectRooms(tiles, first, second) {
    const turnFirst = Math.random() < 0.5;
    if (turnFirst) {
      carveHorizontal(tiles, first.x, second.x, first.y);
      carveVertical(tiles, first.y, second.y, second.x);
    } else {
      carveVertical(tiles, first.y, second.y, first.x);
      carveHorizontal(tiles, first.x, second.x, second.y);
    }
  }
  
  function carveHorizontal(tiles, startX, endX, y) {
    for (let x = Math.min(startX, endX); x <= Math.max(startX, endX); x++) {
      tiles[y][x] = ROGUE_TILES.FLOOR;
    }
  }
  
  function carveVertical(tiles, startY, endY, x) {
    for (let y = Math.min(startY, endY); y <= Math.max(startY, endY); y++) {
      tiles[y][x] = ROGUE_TILES.FLOOR;
    }
  }
  
  function placeDoors(tiles) {
    const candidates = [];
    for (let y = 1; y < tiles.length - 1; y++) {
      for (let x = 1; x < tiles[y].length - 1; x++) {
        if (tiles[y][x] !== ROGUE_TILES.FLOOR) {
          continue;
        }
        const horizontalWalls = tiles[y][x - 1] === ROGUE_TILES.WALL && tiles[y][x + 1] === ROGUE_TILES.WALL;
        const verticalWalls = tiles[y - 1][x] === ROGUE_TILES.WALL && tiles[y + 1][x] === ROGUE_TILES.WALL;
        if (horizontalWalls || verticalWalls) {
          candidates.push({ x, y });
        }
      }
    }
    for (const candidate of candidates) {
      if (Math.random() <= 0.35) {
        tiles[candidate.y][candidate.x] = ROGUE_TILES.DOOR;
      }
    }
    if (candidates.length > 0 && !tiles.some((row) => row.includes(ROGUE_TILES.DOOR))) {
      const fallback = candidates[randomInt(0, candidates.length - 1)];
      tiles[fallback.y][fallback.x] = ROGUE_TILES.DOOR;
      return;
    }
    if (candidates.length === 0) {
      const floorTiles = [];
      for (let y = 1; y < tiles.length - 1; y++) {
        for (let x = 1; x < tiles[y].length - 1; x++) {
          if (tiles[y][x] === ROGUE_TILES.FLOOR) {
            floorTiles.push({ x, y });
          }
        }
      }
      if (floorTiles.length > 0) {
        const fallback = floorTiles[randomInt(0, floorTiles.length - 1)];
        tiles[fallback.y][fallback.x] = ROGUE_TILES.DOOR;
      }
    }
  }
  
  function placeMonsters(tiles, rooms, level) {
    const monsters = [];
    const targetCount = Math.min(rooms.length + level, 8);
    for (let index = 0; index < targetCount && rooms.length > 0; index++) {
      const room = rooms[index % rooms.length];
      const position = randomWalkablePositionInRoom(tiles, room, monsters);
      if (position === null) {
        continue;
      }
      const template = ROGUE_MONSTER_TEMPLATES[randomInt(0, ROGUE_MONSTER_TEMPLATES.length - 1)];
      monsters.push({
        id: `monster-${index}`,
        name: template.name,
        x: position.x,
        y: position.y,
        attack: template.attack + Math.floor(level / 2),
        defense: template.defense + Math.floor(level / 4),
        hp: template.hp + level * 2,
        maxHp: template.hp + level * 2,
        speed: template.speed,
        energy: 0
      });
    }
    return monsters;
  }
  
  function randomWalkablePositionInRoom(tiles, room, monsters) {
    for (let attempt = 0; attempt < 30; attempt++) {
      const position = {
        x: randomInt(room.x, room.x + room.width - 1),
        y: randomInt(room.y, room.y + room.height - 1)
      };
      if (isMonsterWalkableTile(tiles[position.y][position.x]) && monsterAt(position.x, position.y, monsters) === null) {
        return position;
      }
    }
    return null;
  }
  
  function renderRogueDungeon() {
    const rogue = state.rogue;
    const visible = visibleRoguePositions(rogue);
    markRogueExplored(rogue, visible);
    app.innerHTML = `
      <section class="rogue-screen">
        <header class="rogue-topbar">
          <button data-action="menu">Menu</button>
          <div class="rogue-stat"><span>Level</span><strong>${rogue.level}</strong></div>
          <div class="rogue-stat"><span>HP</span><strong>${rogue.player.hp}/${rogue.player.maxHp}</strong></div>
          <div class="rogue-stat"><span>ATK</span><strong>${rogue.player.attack}</strong></div>
          <div class="rogue-stat"><span>DEF</span><strong>${rogue.player.defense}</strong></div>
          <div class="rogue-stat"><span>SPD</span><strong>${rogue.player.speed}</strong></div>
        </header>
        <div class="rogue-map" role="grid" aria-label="Dungeon map">
          ${rogueViewportCells(rogue).map((cell) => rogueCellHtml(rogue, visible, rogue.explored, cell.x, cell.y)).join("")}
        </div>
        <footer class="rogue-bottom">
          <p class="rogue-message">${escapeHtml(rogue.message)}</p>
          <div class="rogue-controls" aria-label="Move controls">
            <span></span>
            <button data-move="up" aria-label="Move up">Up</button>
            <span></span>
            <button data-move="left" aria-label="Move left">Left</button>
            <span></span>
            <button data-move="right" aria-label="Move right">Right</button>
            <span></span>
            <button data-move="down" aria-label="Move down">Down</button>
            <span></span>
          </div>
          <div class="rogue-legend">
            <span><b class="legend-player">P</b> Player</span>
            <span><b class="legend-monster">M</b> Monster</span>
            <span><b class="legend-door">D</b> Door</span>
            <span><b class="legend-exit">E</b> Exit</span>
          </div>
        </footer>
      </section>
    `;
    one("[data-action='menu']").addEventListener("click", exitRogueToMenu);
    for (const button of all("[data-move]")) {
      button.addEventListener("click", () => takeRogueTurn(button.dataset.move));
    }
    window.onkeydown = handleRogueKeyboard;
  }
  
  function rogueViewportCells(rogue) {
    const cells = [];
    const startX = rogue.player.x - ROGUE_VIEWPORT_PLAYER_OFFSET;
    const startY = rogue.player.y - ROGUE_VIEWPORT_PLAYER_OFFSET;
    for (let row = 0; row < ROGUE_VIEWPORT_SIZE; row++) {
      for (let column = 0; column < ROGUE_VIEWPORT_SIZE; column++) {
        cells.push({ x: startX + column, y: startY + row });
      }
    }
    return cells;
  }

  function rogueCellHtml(rogue, visible, explored, x, y) {
    if (!isInsideDungeon(rogue, x, y)) {
      return `<span class="rogue-cell unseen" role="gridcell"></span>`;
    }
    const tile = rogue.tiles[y][x];
    const visibleKey = positionKey(x, y);
    const isVisible = visible.has(visibleKey);
    if (!explored.has(visibleKey)) {
      return `<span class="rogue-cell unseen" role="gridcell"></span>`;
    }
    if (isVisible && rogue.player.x === x && rogue.player.y === y) {
      return `<span class="rogue-cell player" role="gridcell">P</span>`;
    }
    const monster = isVisible ? monsterAt(x, y, rogue.monsters) : null;
    if (monster !== null) {
      return `<span class="rogue-cell monster" role="gridcell">M</span>`;
    }
    const labels = {
      [ROGUE_TILES.WALL]: "#",
      [ROGUE_TILES.FLOOR]: ".",
      [ROGUE_TILES.DOOR]: "D",
      [ROGUE_TILES.EXIT]: "E"
    };
    return `<span class="rogue-cell ${escapeHtml(tile)}${isVisible ? "" : " explored"}" role="gridcell">${labels[tile]}</span>`;
  }
  
  function handleRogueKeyboard(event) {
    const keyDirections = {
      ArrowUp: "up",
      w: "up",
      ArrowLeft: "left",
      a: "left",
      ArrowRight: "right",
      d: "right",
      ArrowDown: "down",
      s: "down"
    };
    const direction = keyDirections[event.key];
    if (!direction || isRoguePromptActive()) {
      return;
    }
    event.preventDefault();
    takeRogueTurn(direction);
  }
  
  function takeRogueTurn(directionName) {
    const rogue = state.rogue;
    const direction = ROGUE_DIRECTIONS[directionName];
    if (!rogue || !direction) {
      return;
    }
    const target = { x: rogue.player.x + direction.x, y: rogue.player.y + direction.y };
    if (!isInsideDungeon(rogue, target.x, target.y)) {
      resolveBlockedRogueMove("A wall blocks the path.");
      return;
    }
    const monster = monsterAt(target.x, target.y, rogue.monsters);
    if (monster !== null) {
      beginRogueFight(monster);
      return;
    }
    const tile = rogue.tiles[target.y][target.x];
    if (tile === ROGUE_TILES.DOOR) {
      beginRogueDoorQuestion(target);
      return;
    }
    if (!isWalkableTile(tile)) {
      resolveBlockedRogueMove("A wall blocks the path.");
      return;
    }
    rogue.player.x = target.x;
    rogue.player.y = target.y;
    if (tile === ROGUE_TILES.EXIT) {
      state.rogue = createRogueGame(rogue.level + 1);
      state.rogue.message = `Level ${rogue.level + 1}. The monsters are stronger.`;
      renderRogueDungeon();
      return;
    }
    rogue.turn++;
    if (moveRogueMonsters()) {
      return;
    }
    if (rogue.player.hp <= 0) {
      renderRogueGameOver();
      return;
    }
    renderRogueDungeon();
  }

  function resolveBlockedRogueMove(message) {
    const rogue = state.rogue;
    rogue.message = message;
    rogue.turn++;
    if (moveRogueMonsters()) {
      return;
    }
    if (rogue.player.hp <= 0) {
      renderRogueGameOver();
      return;
    }
    renderRogueDungeon();
  }
  
  function moveRogueMonsters() {
    const rogue = state.rogue;
    for (const monster of [...rogue.monsters]) {
      if (rogue.player.hp <= 0) {
        return false;
      }
      if (moveRogueMonster(monster)) {
        return true;
      }
    }
    return false;
  }
  
  function moveRogueMonster(monster) {
    const rogue = state.rogue;
    const distance = Math.abs(monster.x - rogue.player.x) + Math.abs(monster.y - rogue.player.y);
    if (distance === 1) {
      beginRogueMonsterAttack(monster);
      return true;
    }
    for (let step = 0; step < monster.speed; step++) {
      if (!moveRogueMonsterStep(monster)) {
        break;
      }
      if (distanceToPlayer(monster) <= 1) {
        break;
      }
    }
    return false;
  }

  function moveRogueMonsterStep(monster) {
    if (distanceToPlayer(monster) > 6) {
      return false;
    }
    const options = Object.values(ROGUE_DIRECTIONS)
      .map((direction) => ({ x: monster.x + direction.x, y: monster.y + direction.y }))
      .filter((position) => canMonsterMoveTo(position.x, position.y, monster.id))
      .sort((first, second) => distanceToPlayer(first) - distanceToPlayer(second));
    const next = options[0];
    if (!next) {
      return false;
    }
    monster.x = next.x;
    monster.y = next.y;
    return true;
  }

  function isRoguePromptActive() {
    return state.rogue?.combat !== null
      || state.rogue?.doorChallenge !== null
      || state.rogue?.monsterAttack !== null;
  }
  
  function canMonsterMoveTo(x, y, monsterId) {
    const rogue = state.rogue;
    return isInsideDungeon(rogue, x, y)
      && isMonsterWalkableTile(rogue.tiles[y][x])
      && !(rogue.player.x === x && rogue.player.y === y)
      && rogue.monsters.every((monster) => monster.id === monsterId || monster.x !== x || monster.y !== y);
  }
  
  function distanceToPlayer(position) {
    const player = state.rogue.player;
    return Math.abs(position.x - player.x) + Math.abs(position.y - player.y);
  }

  function beginRogueMonsterAttack(monster) {
    state.rogue.monsterAttack = {
      monsterId: monster.id,
      entry: nextEntry(),
      direction: resolveDirection()
    };
    renderRogueMonsterAttack();
    speakRogueMonsterAttackQuestion();
  }

  function renderRogueMonsterAttack() {
    const attack = state.rogue.monsterAttack;
    const monster = monsterById(attack.monsterId);
    const asksHebrew = attack.direction === DIRECTIONS.HEBREW_TO_ARABIC;
    app.innerHTML = `
      <section class="screen">
        <div class="panel rogue-fight">
          <div class="top-row">
            <div>
              <p class="eyebrow">Monster attack</p>
              <h2>${escapeHtml(monster.name)} attacks</h2>
            </div>
            ${replayButtonHtml()}
          </div>
          ${rogueEnemyStatusHtml(monster)}
          <p class="question-prefix">${asksHebrew ? "מה זה בערבית?" : "Translate to Hebrew"}</p>
          <p class="question-word" lang="${asksHebrew ? "he" : "ar"}" dir="rtl">${escapeHtml(rogueMonsterAttackQuestionWord(attack))}</p>
          <p class="question-detail">${escapeHtml(challengeDetailText(attack.entry))}</p>
          <form class="answer-form" id="rogueDefenseForm">
            <input id="rogueDefenseInput" type="text" autocomplete="off" dir="auto" aria-label="Answer">
            <button type="submit">Defend</button>
          </form>
          <div class="rogue-combat-stats">You: ${state.rogue.player.hp}/${state.rogue.player.maxHp} HP</div>
        </div>
      </section>
    `;
    one("[data-action='replay']").addEventListener("click", speakRogueMonsterAttackQuestion);
    one("#rogueDefenseForm").addEventListener("submit", (event) => {
      event.preventDefault();
      resolveRogueMonsterAttack(one("#rogueDefenseInput").value);
    });
    one("#rogueDefenseInput").focus();
  }

  function resolveRogueMonsterAttack(answerText) {
    const rogue = state.rogue;
    const attack = rogue.monsterAttack;
    const monster = monsterById(attack.monsterId);
    const comparison = compareRogueChallenge(attack.entry, attack.direction, answerText);
    const mistakes = comparison.totalErrors ?? comparison.characterErrors;
    const defenseBonus = rogueDefenseBonus(mistakes);
    const damage = Math.max(0, monster.attack - rogue.player.defense - defenseBonus);
    rogue.player.hp -= damage;
    rogue.message = damage === 0 ? `${monster.name}'s attack was blocked.` : `${monster.name} hits you for ${damage}.`;
    state.attempts.push({
      prompt: challengeDisplayText(attack.entry),
      expected: comparison.expectedText,
      characterErrors: comparison.baseLetterErrors ?? comparison.characterErrors,
      signErrors: comparison.signErrors ?? 0
    });
    renderRogueMonsterAttackResult(comparison, attack, monster, mistakes, defenseBonus, damage);
    speakRogueResultAnswer(comparison, attack.direction);
  }

  function renderRogueMonsterAttackResult(comparison, attack, monster, mistakes, defenseBonus, damage) {
    const playerDefeated = state.rogue.player.hp <= 0;
    app.innerHTML = `
      <section class="screen">
        <div class="panel">
          <p class="eyebrow">Defense result</p>
          <h2>${damage === 0 ? "Blocked" : "Hit"}</h2>
          ${rogueEnemyStatusHtml(monster)}
          <div class="feedback-grid">
            <div class="feedback-line">
              <span>Expected:</span>
              <span class="answer-replay-row">
                <span class="feedback-value correct-text" dir="rtl">${expectedFeedbackHtml(comparison)}</span>
                ${replayButtonHtml("Replay answer voice")}
              </span>
            </div>
            <div class="feedback-line">
              <span>User:</span>
              <span class="feedback-value" dir="rtl">${userFeedbackHtml(comparison)}</span>
            </div>
            <p class="${damage === 0 ? "success" : "error"}">${mistakes} mistakes, defense +${defenseBonus}</p>
            <p class="muted">${escapeHtml(monster.name)} dealt ${damage} damage.</p>
            <p class="muted">You: ${state.rogue.player.hp}/${state.rogue.player.maxHp} HP</p>
          </div>
          <div class="result-actions">
            <button data-action="continue">${playerDefeated ? "Game over" : "Continue"}</button>
          </div>
        </div>
      </section>
    `;
    one("[data-action='replay']").addEventListener("click", () => speakRogueResultAnswer(comparison, attack.direction));
    one("[data-action='continue']").addEventListener("click", () => {
      state.rogue.monsterAttack = null;
      if (playerDefeated) {
        renderRogueGameOver();
        return;
      }
      renderRogueDungeon();
    });
  }
  
  function beginRogueDoorQuestion(target) {
    state.rogue.doorChallenge = {
      x: target.x,
      y: target.y,
      entry: nextEntry(),
      direction: resolveDirection()
    };
    renderRogueDoorQuestion();
    speakRogueDoorQuestion();
  }
  
  function renderRogueDoorQuestion() {
    const challenge = state.rogue.doorChallenge;
    const asksHebrew = challenge.direction === DIRECTIONS.HEBREW_TO_ARABIC;
    app.innerHTML = `
      <section class="screen">
        <div class="panel rogue-fight">
          <div class="top-row">
            <div>
              <p class="eyebrow">Locked door</p>
              <h2>Answer to open</h2>
            </div>
            ${replayButtonHtml()}
          </div>
          <p class="question-prefix">${asksHebrew ? "מה זה בערבית?" : "Translate to Hebrew"}</p>
          <p class="question-word" lang="${asksHebrew ? "he" : "ar"}" dir="rtl">${escapeHtml(rogueDoorQuestionWord(challenge))}</p>
          <p class="question-detail">${escapeHtml(challengeDetailText(challenge.entry))}</p>
          <form class="answer-form" id="rogueDoorForm">
            <input id="rogueDoorInput" type="text" autocomplete="off" dir="auto" aria-label="Answer">
            <button type="submit">Open</button>
          </form>
        </div>
      </section>
    `;
    one("[data-action='replay']").addEventListener("click", speakRogueDoorQuestion);
    one("#rogueDoorForm").addEventListener("submit", (event) => {
      event.preventDefault();
      resolveRogueDoorQuestion(one("#rogueDoorInput").value);
    });
    one("#rogueDoorInput").focus();
  }
  
  function resolveRogueDoorQuestion(answerText) {
    const rogue = state.rogue;
    const challenge = rogue.doorChallenge;
    const playerHpBeforeMonsterTurn = rogue.player.hp;
    const comparison = compareRogueChallenge(challenge.entry, challenge.direction, answerText);
    const opened = comparison.correct;
    if (opened) {
      rogue.tiles[challenge.y][challenge.x] = ROGUE_TILES.FLOOR;
      rogue.player.x = challenge.x;
      rogue.player.y = challenge.y;
      rogue.message = "The door opens.";
    } else {
      rogue.message = "The door stays locked.";
    }
    state.attempts.push({
      prompt: challengeDisplayText(challenge.entry),
      expected: comparison.expectedText,
      characterErrors: comparison.baseLetterErrors ?? comparison.characterErrors,
      signErrors: comparison.signErrors ?? 0
    });
    rogue.doorChallenge = null;
    rogue.turn++;
    if (moveRogueMonsters()) {
      return;
    }
    renderRogueDoorResult(comparison, challenge, opened, playerHpBeforeMonsterTurn);
    speakRogueResultAnswer(comparison, challenge.direction);
  }
  
  function renderRogueDoorResult(comparison, challenge, opened, playerHpBeforeMonsterTurn) {
    const mistakes = comparison.totalErrors ?? comparison.characterErrors;
    const playerDefeated = state.rogue.player.hp <= 0;
    const monsterTurnText = rogueDoorMonsterTurnText(playerHpBeforeMonsterTurn);
    app.innerHTML = `
      <section class="screen">
        <div class="panel">
          <p class="eyebrow">Door result</p>
          <h2>${opened ? "Door opened" : "Door locked"}</h2>
          <div class="feedback-grid">
            <div class="feedback-line">
              <span>Expected:</span>
              <span class="answer-replay-row">
                <span class="feedback-value correct-text" dir="rtl">${expectedFeedbackHtml(comparison)}</span>
                ${replayButtonHtml("Replay answer voice")}
              </span>
            </div>
            <div class="feedback-line">
              <span>User:</span>
              <span class="feedback-value" dir="rtl">${userFeedbackHtml(comparison)}</span>
            </div>
            <p class="${opened ? "success" : "error"}">${opened ? "Correct. The door opens." : `${mistakes} mistakes. The door stays closed.`}</p>
            <p class="muted">Opening a door used your turn. ${escapeHtml(monsterTurnText)}</p>
            <p class="muted">You: ${state.rogue.player.hp}/${state.rogue.player.maxHp} HP</p>
          </div>
          <div class="result-actions">
            <button data-action="continue">${playerDefeated ? "Game over" : "Continue"}</button>
          </div>
        </div>
      </section>
    `;
    one("[data-action='replay']").addEventListener("click", () => speakRogueResultAnswer(comparison, challenge.direction));
    one("[data-action='continue']").addEventListener("click", () => {
      if (playerDefeated) {
        renderRogueGameOver();
        return;
      }
      renderRogueDungeon();
    });
  }

  function rogueDoorMonsterTurnText(playerHpBeforeMonsterTurn) {
    const damage = playerHpBeforeMonsterTurn - state.rogue.player.hp;
    if (damage > 0) {
      return `A monster attacked for ${damage} damage.`;
    }
    return "Monsters moved after your answer.";
  }
  
  function beginRogueFight(monster) {
    state.rogue.combat = {
      monsterId: monster.id,
      entry: nextEntry(),
      direction: resolveDirection(),
      answer: "",
      feedback: null
    };
    renderRogueFight();
    speakRogueQuestion();
  }
  
  function renderRogueFight() {
    const combat = state.rogue.combat;
    const monster = monsterById(combat.monsterId);
    const asksHebrew = combat.direction === DIRECTIONS.HEBREW_TO_ARABIC;
    app.innerHTML = `
      <section class="screen">
        <div class="panel rogue-fight">
          <div class="top-row">
            <div>
              <p class="eyebrow">Fight</p>
              <h2>${escapeHtml(monster.name)}</h2>
            </div>
            ${replayButtonHtml()}
            <div class="rogue-stat"><span>HP</span><strong>${monster.hp}/${monster.maxHp}</strong></div>
          </div>
          ${rogueEnemyStatusHtml(monster)}
          <p class="question-prefix">${asksHebrew ? "מה זה בערבית?" : "Translate to Hebrew"}</p>
          <p class="question-word" lang="${asksHebrew ? "he" : "ar"}" dir="rtl">${escapeHtml(rogueQuestionWord(combat))}</p>
          <p class="question-detail">${escapeHtml(challengeDetailText(combat.entry))}</p>
          <form class="answer-form" id="rogueAnswerForm">
            <input id="rogueAnswerInput" type="text" autocomplete="off" dir="auto" aria-label="Answer">
            <button type="submit">Attack</button>
          </form>
          <div class="rogue-combat-stats">You: ${state.rogue.player.hp}/${state.rogue.player.maxHp} HP</div>
        </div>
      </section>
    `;
    one("[data-action='replay']").addEventListener("click", speakRogueQuestion);
    one("#rogueAnswerForm").addEventListener("submit", (event) => {
      event.preventDefault();
      resolveRogueFight(one("#rogueAnswerInput").value);
    });
    one("#rogueAnswerInput").focus();
  }
  
  function resolveRogueFight(answerText) {
    const rogue = state.rogue;
    const combat = rogue.combat;
    const monster = monsterById(combat.monsterId);
    const comparison = combat.direction === DIRECTIONS.HEBREW_TO_ARABIC
      ? compareArabic(combat.entry, answerText)
      : compareHebrew(combat.entry.hebrew, answerText);
    const mistakes = comparison.totalErrors ?? comparison.characterErrors;
    const attackModifier = rogueAttackModifier(mistakes);
    const playerDamage = Math.max(1, rogue.player.attack + attackModifier - monster.defense);
    monster.hp -= playerDamage;
    const monsterDefeated = monster.hp <= 0;
    if (monsterDefeated) {
      rogue.monsters = rogue.monsters.filter((item) => item.id !== monster.id);
    }
    state.attempts.push({
      prompt: challengeDisplayText(combat.entry),
      expected: comparison.expectedText,
      characterErrors: comparison.baseLetterErrors ?? comparison.characterErrors,
      signErrors: comparison.signErrors ?? 0
    });
    renderRogueFightResult(comparison, mistakes, attackModifier, playerDamage, monster);
    speakRogueResultAnswer(comparison, combat.direction);
  }
  
  function renderRogueFightResult(comparison, mistakes, attackModifier, playerDamage, monster) {
    const defeated = monster.hp <= 0;
    app.innerHTML = `
      <section class="screen">
        <div class="panel">
          <p class="eyebrow">Combat result</p>
          <h2>${defeated ? "Monster defeated" : "Hit exchanged"}</h2>
          ${rogueEnemyStatusHtml(monster)}
          <div class="feedback-grid">
            <div class="feedback-line">
              <span>Expected:</span>
              <span class="answer-replay-row">
                <span class="feedback-value correct-text" dir="rtl">${expectedFeedbackHtml(comparison)}</span>
                ${replayButtonHtml("Replay answer voice")}
              </span>
            </div>
            <div class="feedback-line">
              <span>User:</span>
              <span class="feedback-value" dir="rtl">${userFeedbackHtml(comparison)}</span>
            </div>
            <p class="${mistakes === 0 ? "success" : "error"}">${mistakes} mistakes, attack ${attackModifier >= 0 ? "+" : ""}${attackModifier}</p>
            <p class="muted">You dealt ${playerDamage} damage.${defeated ? "" : ` ${escapeHtml(monster.name)} is about to attack.`}</p>
          </div>
          <div class="result-actions">
            <button data-action="continue">${defeated ? "Continue" : "Defend"}</button>
          </div>
        </div>
      </section>
    `;
    one("[data-action='replay']").addEventListener("click", () => speakRogueResultAnswer(comparison, state.rogue.combat.direction));
    one("[data-action='continue']").addEventListener("click", () => {
      state.rogue.combat = null;
      state.rogue.message = defeated ? `${monster.name} was defeated.` : `${monster.name} is still standing.`;
      if (!defeated) {
        beginRogueMonsterAttack(monster);
        return;
      }
      renderRogueDungeon();
    });
  }
  
  function rogueEnemyStatusHtml(monster) {
    return `
      <section class="rogue-enemy-status" aria-label="Enemy status">
        <div>
          <p class="eyebrow">Enemy status</p>
          <h3>${escapeHtml(monster.name)}</h3>
        </div>
        <div class="rogue-status-grid">
          <div class="rogue-stat"><span>HP</span><strong>${Math.max(0, monster.hp)}/${monster.maxHp}</strong></div>
          <div class="rogue-stat"><span>Attack</span><strong>${monster.attack}</strong></div>
          <div class="rogue-stat"><span>Defense</span><strong>${monster.defense}</strong></div>
          <div class="rogue-stat"><span>Speed</span><strong>${monster.speed}</strong></div>
        </div>
      </section>
    `;
  }
  
  function renderRogueGameOver() {
    window.onkeydown = null;
    app.innerHTML = `
      <section class="screen">
        <div class="panel">
          <p class="eyebrow">Rogue</p>
          <h2>Game over</h2>
          <p class="muted">You reached level ${state.rogue.level} and answered ${state.attempts.length} fight questions.</p>
          <div class="result-actions">
            <button data-action="restart">Restart Rogue</button>
            <button data-action="menu">Menu</button>
          </div>
        </div>
      </section>
    `;
    one("[data-action='restart']").addEventListener("click", startRogueGame);
    one("[data-action='menu']").addEventListener("click", exitRogueToMenu);
  }
  
  function rogueQuestionWord(combat) {
    return combat.direction === DIRECTIONS.ARABIC_TO_HEBREW ? combat.entry.arabic : combat.entry.hebrew;
  }
  
  function rogueDoorQuestionWord(challenge) {
    return challenge.direction === DIRECTIONS.ARABIC_TO_HEBREW ? challenge.entry.arabic : challenge.entry.hebrew;
  }

  function rogueMonsterAttackQuestionWord(attack) {
    return attack.direction === DIRECTIONS.ARABIC_TO_HEBREW ? attack.entry.arabic : attack.entry.hebrew;
  }
  
  function compareRogueChallenge(entry, direction, answerText) {
    return direction === DIRECTIONS.HEBREW_TO_ARABIC
      ? compareArabic(entry, answerText)
      : compareHebrew(entry.hebrew, answerText);
  }
  
  function speakRogueDoorQuestion() {
    const challenge = state.rogue?.doorChallenge;
    if (!challenge) {
      return;
    }
    if (challenge.direction === DIRECTIONS.ARABIC_TO_HEBREW) {
      speakText(challenge.entry.arabic, "ar", { rate: 0.95 });
    } else {
      speakText(["מה זה בערבית?", challenge.entry.hebrew, spokenDetail(challenge.entry)], "he-IL", { rate: 0.72, gapMs: 260 });
    }
  }

  function speakRogueMonsterAttackQuestion() {
    const attack = state.rogue?.monsterAttack;
    if (!attack) {
      return;
    }
    if (attack.direction === DIRECTIONS.ARABIC_TO_HEBREW) {
      speakText(attack.entry.arabic, "ar", { rate: 0.95 });
    } else {
      speakText(["מה זה בערבית?", attack.entry.hebrew, spokenDetail(attack.entry)], "he-IL", { rate: 0.72, gapMs: 260 });
    }
  }
  
  function speakRogueQuestion() {
    const combat = state.rogue?.combat;
    if (!combat) {
      return;
    }
    if (combat.direction === DIRECTIONS.ARABIC_TO_HEBREW) {
      speakText(combat.entry.arabic, "ar", { rate: 0.95 });
    } else {
      speakText(["מה זה בערבית?", combat.entry.hebrew, spokenDetail(combat.entry)], "he-IL", { rate: 0.72, gapMs: 260 });
    }
  }
  
  function speakRogueResultAnswer(comparison, direction) {
    if (direction === DIRECTIONS.HEBREW_TO_ARABIC) {
      speakText(comparison.expectedText, "ar", { rate: 0.95 });
    } else {
      speakText(comparison.expectedText, "he-IL", { rate: 0.78 });
    }
  }
  
  function rogueAttackModifier(mistakes) {
    if (mistakes === 0) {
      return 5;
    }
    if (mistakes === 1) {
      return 3;
    }
    if (mistakes === 2) {
      return 1;
    }
    return -2;
  }

  function rogueDefenseBonus(mistakes) {
    if (mistakes === 0) {
      return 5;
    }
    if (mistakes === 1) {
      return 3;
    }
    if (mistakes === 2) {
      return 1;
    }
    return 0;
  }
  
  function visibleRoguePositions(rogue) {
    const visible = new Set();
    for (let y = rogue.player.y - 5; y <= rogue.player.y + 5; y++) {
      for (let x = rogue.player.x - 5; x <= rogue.player.x + 5; x++) {
        if (isInsideDungeon(rogue, x, y) && hasRogueLineOfSight(rogue, x, y)) {
          visible.add(positionKey(x, y));
        }
      }
    }
    return visible;
  }

  function markRogueExplored(rogue, visible) {
    for (const key of visible) {
      rogue.explored.add(key);
    }
  }

  function hasRogueLineOfSight(rogue, targetX, targetY) {
    const points = lineBetween(rogue.player.x, rogue.player.y, targetX, targetY);
    for (let index = 1; index < points.length - 1; index++) {
      const point = points[index];
      if (blocksRogueSight(rogue.tiles[point.y][point.x])) {
        return false;
      }
    }
    return true;
  }
  
  function lineBetween(startX, startY, endX, endY) {
    const points = [];
    let x = startX;
    let y = startY;
    const dx = Math.abs(endX - startX);
    const dy = Math.abs(endY - startY);
    const stepX = startX < endX ? 1 : -1;
    const stepY = startY < endY ? 1 : -1;
    let error = dx - dy;
    while (true) {
      points.push({ x, y });
      if (x === endX && y === endY) {
        return points;
      }
      const doubleError = error * 2;
      if (doubleError > -dy) {
        error -= dy;
        x += stepX;
      }
      if (doubleError < dx) {
        error += dx;
        y += stepY;
      }
    }
  }
  
  function positionKey(x, y) {
    return `${x},${y}`;
  }
  
  function monsterAt(x, y, monsters) {
    return monsters.find((monster) => monster.x === x && monster.y === y) ?? null;
  }
  
  function monsterById(id) {
    return state.rogue.monsters.find((monster) => monster.id === id);
  }
  
  function isInsideDungeon(rogue, x, y) {
    return x >= 0 && y >= 0 && x < rogue.width && y < rogue.height;
  }
  
  function isWalkableTile(tile) {
    return tile === ROGUE_TILES.FLOOR || tile === ROGUE_TILES.DOOR || tile === ROGUE_TILES.EXIT;
  }
  
  function isMonsterWalkableTile(tile) {
    return tile === ROGUE_TILES.FLOOR || tile === ROGUE_TILES.EXIT;
  }
  
  function blocksRogueSight(tile) {
    return tile === ROGUE_TILES.WALL || tile === ROGUE_TILES.DOOR;
  }
  
  function roomCenter(room) {
    return {
      x: Math.floor(room.x + room.width / 2),
      y: Math.floor(room.y + room.height / 2)
    };
  }
  
  function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  return {
    startRogueGame
  };
}
