const STORAGE_KEY = "ellasgame.web.settings";
const DIRECTIONS = {
  HEBREW_TO_ARABIC: "hebrew_to_arabic",
  ARABIC_TO_HEBREW: "arabic_to_hebrew",
  RANDOM_BOTH: "random_both"
};
const NUMBER_LABELS = {
  single: "יחיד",
  plural: "רבים"
};
const GENDER_LABELS = {
  female: "נקבה",
  male: "זכר",
  "N/A": ""
};

const app = document.querySelector("#app");
const state = {
  vocabulary: [],
  groups: [],
  pages: [],
  pageCounts: new Map(),
  settings: loadSettings(),
  cameraDevices: [],
  deck: [],
  deckGroupsKey: "",
  deckPagesKey: "",
  lastEntry: null,
  current: null,
  currentDirection: DIRECTIONS.HEBREW_TO_ARABIC,
  attempts: [],
  cameraStream: null,
  snapshot: null,
  region: null
};

main().catch((error) => {
  console.error(error);
  renderError("Could not start the web app.");
});

async function main() {
  state.vocabulary = await loadVocabulary();
  state.groups = unique(state.vocabulary.map((entry) => entry.group));
  state.pages = unique(state.vocabulary.map((entry) => entry.page)).sort((first, second) => Number(first) - Number(second));
  state.pageCounts = new Map(state.pages.map((page) => [page, state.vocabulary.filter((entry) => entry.page === page).length]));
  renderMenu();
}

async function loadVocabulary() {
  const response = await fetch("/vocabulary.json", { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`Vocabulary load failed: ${response.status}`);
  }
  return response.json();
}

function renderMenu() {
  stopCamera();
  app.innerHTML = `
    <section class="screen">
      <div class="panel">
        <div class="menu-actions">
          <button class="menu-action" data-action="settings">
            <img class="menu-icon" src="/icons/settings.png" alt="" aria-hidden="true">
            <span class="menu-label">Settings</span>
          </button>
          <button class="menu-action" data-action="play">
            <img class="menu-icon" src="/icons/play.png" alt="" aria-hidden="true">
            <span class="menu-label">Play</span>
          </button>
          <p class="selected-count">${entryCountForSelection()} words selected</p>
        </div>
      </div>
    </section>
  `;
  one("[data-action='settings']").addEventListener("click", renderSettingsDialog);
  one("[data-action='play']").addEventListener("click", startSession);
}

function renderSettingsDialog() {
  refreshCameraDevices().then(updateCameraOptions).catch(() => updateCameraOptions());
  const selectedGroups = new Set(state.settings.vocabularyGroups);
  const selectedPages = new Set(state.settings.vocabularyPages);
  app.insertAdjacentHTML("beforeend", `
    <div class="dialog-backdrop" role="dialog" aria-modal="true" aria-label="Settings">
      <div class="panel dialog">
        <div class="top-row">
          <div>
            <p class="eyebrow">Settings</p>
            <h2>Practice setup</h2>
          </div>
          <button data-action="close-settings">Cancel</button>
        </div>
        <div class="settings-grid">
          <label class="setting-row">
            <span>Questions</span>
            <select id="directionSetting">
              <option value="${DIRECTIONS.HEBREW_TO_ARABIC}">Hebrew → Arabic</option>
              <option value="${DIRECTIONS.ARABIC_TO_HEBREW}">Arabic → Hebrew</option>
              <option value="${DIRECTIONS.RANDOM_BOTH}">Random</option>
            </select>
          </label>
          <label class="setting-row">
            <span>Camera</span>
            <select id="cameraSetting">
              ${cameraOptionsHtml()}
            </select>
          </label>
          <button data-action="refresh-cameras">Refresh cameras</button>
          <p class="muted">Browser camera names appear after camera permission is allowed.</p>
          <details class="fold">
            <summary>Groups</summary>
            <div class="checkbox-grid" id="groupChoices">
              <label class="checkbox-row">
                <input type="checkbox" id="allGroups" ${selectedGroups.size === 0 ? "checked" : ""}>
                <span>All groups</span>
                <span></span>
              </label>
              ${state.groups.map((group) => checkboxRow("group", group, selectedGroups.size === 0 || selectedGroups.has(group))).join("")}
            </div>
          </details>
          <details class="fold">
            <summary>Pages</summary>
            <div class="checkbox-grid" id="pageChoices">
              <label class="checkbox-row">
                <input type="checkbox" id="allPages" ${selectedPages.size === 0 ? "checked" : ""}>
                <span>All pages</span>
                <span></span>
              </label>
              ${state.pages.map((page) => checkboxRow("page", page, selectedPages.size === 0 || selectedPages.has(page), `${state.pageCounts.get(page)} words`)).join("")}
            </div>
          </details>
          <button data-action="save-settings">Save</button>
        </div>
      </div>
    </div>
  `);

  one("#directionSetting").value = state.settings.translationDirection;
  one("#cameraSetting").value = state.settings.camera;
  one("[data-action='refresh-cameras']").addEventListener("click", async () => {
    await requestCameraListPermission();
    await refreshCameraDevices();
    updateCameraOptions();
  });
  wireAllCheckbox(one("#allGroups"), all("[data-kind='group']"));
  wireAllCheckbox(one("#allPages"), all("[data-kind='page']"));
  one("[data-action='close-settings']").addEventListener("click", closeDialog);
  one("[data-action='save-settings']").addEventListener("click", () => {
    state.settings = {
      translationDirection: one("#directionSetting").value,
      camera: one("#cameraSetting").value,
      vocabularyGroups: selectedValues(one("#allGroups"), all("[data-kind='group']")),
      vocabularyPages: selectedValues(one("#allPages"), all("[data-kind='page']"))
    };
    saveSettings();
    state.deck = [];
    closeDialog();
    renderMenu();
  });
}

function checkboxRow(kind, value, checked, meta = "") {
  return `
    <label class="checkbox-row">
      <input type="checkbox" data-kind="${escapeHtml(kind)}" value="${escapeHtml(value)}" ${checked ? "checked" : ""}>
      <span>${escapeHtml(value)}</span>
      <span class="muted">${escapeHtml(meta)}</span>
    </label>
  `;
}

function wireAllCheckbox(allCheckbox, checkboxes) {
  allCheckbox.addEventListener("change", () => {
    for (const checkbox of checkboxes) {
      checkbox.checked = allCheckbox.checked;
    }
  });
  for (const checkbox of checkboxes) {
    checkbox.addEventListener("change", () => {
      if (!checkbox.checked) {
        allCheckbox.checked = false;
      } else if (checkboxes.every((item) => item.checked)) {
        allCheckbox.checked = true;
      }
    });
  }
}

function startSession() {
  state.attempts = [];
  startChallenge();
}

function startChallenge() {
  stopCamera();
  state.current = nextEntry();
  state.currentDirection = resolveDirection();
  renderChoice();
  speakQuestion();
}

function renderChoice() {
  const asksHebrew = state.currentDirection === DIRECTIONS.HEBREW_TO_ARABIC;
  app.innerHTML = `
    <section class="screen">
      <div class="panel">
        <div class="question-top">
          <p class="question-prefix">${asksHebrew ? "מה זה בערבית?" : "Translate to Hebrew"}</p>
          ${replayButtonHtml()}
        </div>
        <p class="question-word" lang="${asksHebrew ? "he" : "ar"}" dir="${asksHebrew ? "rtl" : "rtl"}">${escapeHtml(questionWord())}</p>
        <p class="question-detail">${escapeHtml(challengeDetailText(state.current))}</p>
        <div class="choice-grid">
          <button class="choice-button" data-action="camera">
            <img class="choice-icon" src="/icons/camera-steampunk.png" alt="" aria-hidden="true">
            <span>Camera</span>
          </button>
          <button class="choice-button" data-action="keyboard">
            <img class="choice-icon" src="/icons/keyboard-steampunk.png" alt="" aria-hidden="true">
            <span>Keyboard</span>
          </button>
          <button class="choice-button" data-action="sketchpad">
            <img class="choice-icon" src="/icons/sketchpad-steampunk.png" alt="" aria-hidden="true">
            <span>Sketchpad</span>
          </button>
        </div>
        <p class="status" id="choiceStatus"></p>
      </div>
    </section>
  `;
  one("[data-action='replay']").addEventListener("click", speakQuestion);
  disableChoice("camera");
  one("[data-action='keyboard']").addEventListener("click", renderKeyboard);
  disableChoice("sketchpad");
}

function disableChoice(action) {
  const button = one(`[data-action='${action}']`);
  button.disabled = true;
  button.title = "Coming soon";
}

function renderKeyboard() {
  app.innerHTML = `
    <section class="screen">
      <div class="panel">
        <div class="toolbar">
          <button data-action="back">Back</button>
          ${replayButtonHtml()}
        </div>
        <p class="question-prefix">${state.currentDirection === DIRECTIONS.ARABIC_TO_HEBREW ? "Hebrew word" : "Arabic word"}</p>
        <p class="question-word" lang="${state.currentDirection === DIRECTIONS.ARABIC_TO_HEBREW ? "ar" : "he"}" dir="rtl">${escapeHtml(questionWord())}</p>
        <form class="answer-form" id="answerForm">
          <input id="answerInput" type="text" autocomplete="off" dir="auto" aria-label="Answer">
          <button type="submit">Ready</button>
        </form>
      </div>
    </section>
  `;
  one("[data-action='back']").addEventListener("click", renderChoice);
  one("[data-action='replay']").addEventListener("click", speakQuestion);
  one("#answerForm").addEventListener("submit", (event) => {
    event.preventDefault();
    showResult(one("#answerInput").value, null);
  });
  one("#answerInput").focus();
}

function replayButtonHtml(label = "Replay voice") {
  return `
    <button class="replay-button" data-action="replay" type="button" aria-label="${escapeHtml(label)}" title="${escapeHtml(label)}">
      <span aria-hidden="true">↻</span>
    </button>
  `;
}

async function renderCamera() {
  app.innerHTML = `
    <section class="surface-panel">
      <div class="toolbar">
        <button data-action="back">Back</button>
        <span class="muted">Camera capture</span>
      </div>
      <div class="camera-wrap">
        <video id="cameraVideo" autoplay playsinline muted></video>
      </div>
      <div class="toolbar">
        <span id="cameraStatus" class="status"></span>
        <button data-action="capture">Capture</button>
      </div>
    </section>
  `;
  one("[data-action='back']").addEventListener("click", () => {
    stopCamera();
    renderChoice();
  });
  try {
    state.cameraStream = await navigator.mediaDevices.getUserMedia(cameraConstraints());
    one("#cameraVideo").srcObject = state.cameraStream;
    await refreshCameraDevices();
  } catch (error) {
    one("#cameraStatus").textContent = "Could not start camera.";
    one("[data-action='capture']").disabled = true;
    return;
  }
  one("[data-action='capture']").addEventListener("click", captureCameraFrame);
}

function captureCameraFrame() {
  const video = one("#cameraVideo");
  const canvas = document.createElement("canvas");
  canvas.width = video.videoWidth || 1280;
  canvas.height = video.videoHeight || 720;
  canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
  state.snapshot = canvas;
  stopCamera();
  renderRegionSelection();
}

function renderRegionSelection() {
  state.region = null;
  app.innerHTML = `
    <section class="surface-panel">
      <div class="toolbar">
        <button data-action="back">Back</button>
        <span class="muted">Select the answer region</span>
      </div>
      <div class="camera-wrap" id="regionWrap">
        <canvas id="regionCanvas"></canvas>
        <div id="regionBox" class="region-box hidden"></div>
      </div>
      <div class="toolbar">
        <span id="regionStatus" class="status">Drag over the answer area.</span>
        <button data-action="ready">Ready</button>
      </div>
    </section>
  `;
  const canvas = one("#regionCanvas");
  const context = canvas.getContext("2d");
  canvas.width = state.snapshot.width;
  canvas.height = state.snapshot.height;
  context.drawImage(state.snapshot, 0, 0);
  wireRegionDrag(one("#regionWrap"), one("#regionBox"));
  one("[data-action='back']").addEventListener("click", renderChoice);
  one("[data-action='ready']").addEventListener("click", () => showResult(expectedAnswer(), state.region));
}

function wireRegionDrag(wrap, box) {
  let start = null;
  wrap.addEventListener("pointerdown", (event) => {
    start = pointInElement(event, wrap);
    updateRegionBox(box, start.x, start.y, 0, 0);
    box.classList.remove("hidden");
    wrap.setPointerCapture(event.pointerId);
  });
  wrap.addEventListener("pointermove", (event) => {
    if (start === null) {
      return;
    }
    const current = pointInElement(event, wrap);
    const left = Math.min(start.x, current.x);
    const top = Math.min(start.y, current.y);
    const width = Math.abs(current.x - start.x);
    const height = Math.abs(current.y - start.y);
    updateRegionBox(box, left, top, width, height);
    state.region = {
      width: Math.round(width),
      height: Math.round(height)
    };
  });
  wrap.addEventListener("pointerup", () => {
    start = null;
  });
}

function renderSketchpad() {
  app.innerHTML = `
    <section class="surface-panel">
      <div class="toolbar">
        <button data-action="back">Back</button>
        <span class="muted">Sketchpad</span>
      </div>
      <div class="canvas-wrap">
        <canvas id="sketchCanvas" tabindex="0"></canvas>
      </div>
      <div class="toolbar">
        <button data-action="clear">Clear</button>
        <button data-action="ready">Ready</button>
      </div>
    </section>
  `;
  const canvas = one("#sketchCanvas");
  resizeSketchCanvas(canvas);
  wireSketchpad(canvas);
  one("[data-action='back']").addEventListener("click", renderChoice);
  one("[data-action='clear']").addEventListener("click", () => clearCanvas(canvas));
  one("[data-action='ready']").addEventListener("click", () => showResult(expectedAnswer(), null));
}

function wireSketchpad(canvas) {
  const context = canvas.getContext("2d");
  context.lineWidth = 6;
  context.lineCap = "round";
  context.strokeStyle = "#f0f4f8";
  let drawing = false;
  canvas.addEventListener("pointerdown", (event) => {
    drawing = true;
    const point = pointInElement(event, canvas);
    context.beginPath();
    context.moveTo(point.x, point.y);
    canvas.setPointerCapture(event.pointerId);
  });
  canvas.addEventListener("pointermove", (event) => {
    if (!drawing) {
      return;
    }
    const point = pointInElement(event, canvas);
    context.lineTo(point.x, point.y);
    context.stroke();
  });
  canvas.addEventListener("pointerup", () => {
    drawing = false;
  });
}

function showResult(answerText, region) {
  stopCamera();
  const comparison = state.currentDirection === DIRECTIONS.HEBREW_TO_ARABIC
    ? compareArabic(state.current, answerText)
    : compareHebrew(state.current.hebrew, answerText);
  const prompt = challengeDisplayText(state.current);
  state.attempts.push({
    prompt,
    expected: comparison.expectedText,
    characterErrors: comparison.baseLetterErrors ?? comparison.characterErrors,
    signErrors: comparison.signErrors ?? 0
  });
  renderResult(comparison, region);
  speakResultAnswer(comparison);
}

function renderResult(comparison, region) {
  const isArabic = state.currentDirection === DIRECTIONS.HEBREW_TO_ARABIC;
  app.innerHTML = `
    <section class="screen">
      <div class="panel">
        <h2 dir="rtl">${escapeHtml(challengeDisplayText(state.current))}</h2>
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
          <p class="${comparison.correct ? "success" : "error"}">${escapeHtml(resultText(comparison))}</p>
          ${isArabic
            ? `<p class="muted">Base letters: ${comparison.baseLetterErrors} errors</p><p class="muted">Signs: ${comparison.signErrors} errors</p>`
            : `<p class="muted">Characters: ${comparison.characterErrors} errors</p>`}
          ${region ? `<p class="muted">Region: ${region.width}x${region.height}</p>` : ""}
        </div>
        <div class="result-actions">
          <button data-action="again">Again</button>
          <button data-action="finish">Finish</button>
        </div>
      </div>
    </section>
  `;
  one("[data-action='again']").addEventListener("click", startChallenge);
  one("[data-action='replay']").addEventListener("click", () => speakResultAnswer(comparison));
  one("[data-action='finish']").addEventListener("click", renderSummary);
}

function renderSummary() {
  const summary = summarizeAttempts();
  app.innerHTML = `
    <section class="screen">
      <div class="panel">
        <p class="eyebrow">Summary</p>
        <h2>סיכום</h2>
        <div class="summary-grid">
          <div class="summary-row"><span>Words</span><strong>${summary.wordCount}</strong></div>
          <div class="summary-row"><span>Perfect scores</span><strong>${summary.perfectScores}</strong></div>
          <div class="summary-row"><span>Words with mistakes</span><strong>${summary.wordsWithMistakes}</strong></div>
          <div class="summary-row"><span>Average mistakes</span><strong>${summary.averageMistakeCount.toFixed(2)}</strong></div>
          <div class="summary-row"><span>Lead error</span><strong>${summary.leadingErrorType}</strong></div>
          <h3>Most errors</h3>
          ${summary.wordsWithMostErrors.length === 0
            ? `<p class="muted">None</p>`
            : summary.wordsWithMostErrors.map((item) => `<p class="muted">${escapeHtml(item.prompt)} / ${escapeHtml(item.expected)}: ${item.totalErrors} errors</p>`).join("")}
        </div>
        <div class="result-actions">
          <button data-action="menu">Menu</button>
        </div>
      </div>
    </section>
  `;
  one("[data-action='menu']").addEventListener("click", () => {
    state.attempts = [];
    renderMenu();
  });
}

function nextEntry() {
  const groupsKey = state.settings.vocabularyGroups.join("\n");
  const pagesKey = state.settings.vocabularyPages.join("\n");
  if (state.deckGroupsKey !== groupsKey || state.deckPagesKey !== pagesKey) {
    state.deck = [];
    state.deckGroupsKey = groupsKey;
    state.deckPagesKey = pagesKey;
  }
  if (state.deck.length === 0) {
    state.deck = entriesForSelection();
    shuffle(state.deck);
    avoidImmediateRepeatAcrossDecks();
  }
  const entry = state.deck.pop();
  state.lastEntry = entry;
  return entry;
}

function entriesForSelection() {
  const groupSet = new Set(state.settings.vocabularyGroups);
  const pageSet = new Set(state.settings.vocabularyPages);
  let candidates = groupSet.size === 0 ? state.vocabulary : state.vocabulary.filter((entry) => groupSet.has(entry.group));
  if (candidates.length === 0) {
    candidates = state.vocabulary;
  }
  const pageCandidates = pageSet.size === 0 ? candidates : candidates.filter((entry) => pageSet.has(entry.page));
  return pageCandidates.length === 0 ? candidates : pageCandidates;
}

function avoidImmediateRepeatAcrossDecks() {
  if (state.lastEntry === null || state.deck.length <= 1) {
    return;
  }
  const nextIndex = state.deck.length - 1;
  if (!sameEntry(state.lastEntry, state.deck[nextIndex])) {
    return;
  }
  [state.deck[nextIndex], state.deck[nextIndex - 1]] = [state.deck[nextIndex - 1], state.deck[nextIndex]];
}

function resolveDirection() {
  if (state.settings.translationDirection !== DIRECTIONS.RANDOM_BOTH) {
    return state.settings.translationDirection;
  }
  return Math.random() < 0.5 ? DIRECTIONS.HEBREW_TO_ARABIC : DIRECTIONS.ARABIC_TO_HEBREW;
}

function questionWord() {
  return state.currentDirection === DIRECTIONS.ARABIC_TO_HEBREW ? state.current.arabic : state.current.hebrew;
}

function expectedAnswer() {
  return state.currentDirection === DIRECTIONS.ARABIC_TO_HEBREW ? state.current.hebrew : state.current.arabic;
}

function speakQuestion() {
  if (state.currentDirection === DIRECTIONS.ARABIC_TO_HEBREW) {
    speakText(state.current.arabic, "ar", { rate: 0.95 });
  } else {
    speakText(["מה זה בערבית?", state.current.hebrew, spokenDetail(state.current)], "he-IL", { rate: 0.72, gapMs: 260 });
  }
}

function speakResultAnswer(comparison) {
  if (state.currentDirection === DIRECTIONS.HEBREW_TO_ARABIC) {
    speakText(comparison.expectedText, "ar", { rate: 0.95 });
  } else {
    speakText(comparison.expectedText, "he-IL", { rate: 0.78 });
  }
}

function speakText(text, lang, options = {}) {
  if (!("speechSynthesis" in window)) {
    return;
  }
  window.speechSynthesis.cancel();
  const parts = Array.isArray(text) ? text.filter(Boolean) : [text];
  speakParts(parts, lang, options, 0);
}

function speakParts(parts, lang, options, index) {
  if (index >= parts.length) {
    return;
  }
  const utterance = new SpeechSynthesisUtterance(parts[index]);
  utterance.lang = lang;
  utterance.rate = options.rate ?? 0.82;
  utterance.onend = () => {
    window.setTimeout(() => speakParts(parts, lang, options, index + 1), options.gapMs ?? 180);
  };
  window.speechSynthesis.speak(utterance);
}

function compareArabic(entry, actual) {
  const answers = entry.arabicAlias && entry.arabicAlias !== "N/A" ? [entry.arabic, entry.arabicAlias] : [entry.arabic];
  return answers.map((answer) => compareArabicExpected(answer, actual)).sort((a, b) =>
    a.totalErrors - b.totalErrors || a.baseLetterErrors - b.baseLetterErrors || a.signErrors - b.signErrors
  )[0];
}

function compareArabicExpected(expected, actual) {
  const expectedUnits = parseArabicUnits(expected);
  const actualUnits = parseArabicUnits(actual);
  const alignment = alignBaseLetters(expectedUnits, actualUnits);
  const userCharacters = [];
  let baseLetterErrors = 0;
  let signErrors = 0;
  for (const step of alignment) {
    if (step.expectedIndex >= 0 && step.actualIndex >= 0) {
      const expectedUnit = expectedUnits[step.expectedIndex];
      const actualUnit = actualUnits[step.actualIndex];
      const baseCorrect = expectedUnit.baseCodePoint === actualUnit.baseCodePoint;
      if (!baseCorrect) {
        baseLetterErrors++;
        signErrors += expectedUnit.signs.length;
        userCharacters.push({ baseText: actualUnit.baseText, baseCorrect: false, signs: signFeedbackForMismatch(expectedUnit.signs, actualUnit.signs) });
      } else {
        const signs = signFeedback(expectedUnit.signs, actualUnit.signs);
        signErrors += signDifferenceCount(expectedUnit.signs, actualUnit.signs);
        userCharacters.push({ baseText: actualUnit.baseText, baseCorrect: true, signs });
      }
    } else if (step.expectedIndex >= 0) {
      baseLetterErrors++;
      signErrors += expectedUnits[step.expectedIndex].signs.length;
    } else {
      const actualUnit = actualUnits[step.actualIndex];
      baseLetterErrors++;
      userCharacters.push({ baseText: actualUnit.baseText, baseCorrect: false, signs: actualUnit.signs.map((sign) => ({ text: codePointText(sign), correct: false })) });
    }
  }
  return {
    expectedText: expected,
    correct: baseLetterErrors === 0 && signErrors === 0,
    baseLetterErrors,
    signErrors,
    totalErrors: baseLetterErrors + signErrors,
    expectedCharacters: expectedUnits.map((unit) => unit.text),
    userCharacters
  };
}

function compareHebrew(expected, actual) {
  const expectedText = sanitizeHebrew(expected);
  const actualText = sanitizeHebrew(actual);
  const expectedCharacters = codePoints(expectedText);
  const actualCharacters = codePoints(actualText);
  const alignment = alignCharacters(expectedCharacters, actualCharacters);
  const userCharacters = [];
  let characterErrors = 0;
  for (const step of alignment) {
    if (step.expectedIndex >= 0 && step.actualIndex >= 0) {
      const correct = expectedCharacters[step.expectedIndex] === actualCharacters[step.actualIndex];
      if (!correct) {
        characterErrors++;
      }
      userCharacters.push({ text: codePointText(actualCharacters[step.actualIndex]), correct });
    } else if (step.expectedIndex >= 0) {
      characterErrors++;
    } else {
      characterErrors++;
      userCharacters.push({ text: codePointText(actualCharacters[step.actualIndex]), correct: false });
    }
  }
  return {
    expectedText,
    correct: characterErrors === 0,
    characterErrors,
    expectedCharacters: expectedCharacters.map(codePointText),
    userCharacters
  };
}

function parseArabicUnits(rawText) {
  const normalized = (rawText ?? "").trim().normalize("NFC");
  const builders = [];
  let current = null;
  let previousWasWhitespace = false;
  for (const character of normalized) {
    const codePoint = character.codePointAt(0);
    if (/\s/u.test(character)) {
      if (!previousWasWhitespace && builders.length > 0) {
        current = arabicUnitBuilder(" ".codePointAt(0));
        builders.push(current);
      }
      previousWasWhitespace = true;
      continue;
    }
    previousWasWhitespace = false;
    if (codePoint === 0x0640) {
      continue;
    }
    if (isArabicSign(codePoint)) {
      if (current !== null) {
        current.signs.push(codePoint);
        current.text += character;
      }
      continue;
    }
    current = arabicUnitBuilder(codePoint);
    builders.push(current);
  }
  return builders.map((builder) => ({
    baseCodePoint: builder.baseCodePoint,
    baseText: codePointText(builder.baseCodePoint),
    text: builder.text,
    signs: builder.signs
  }));
}

function arabicUnitBuilder(baseCodePoint) {
  return { baseCodePoint, text: codePointText(baseCodePoint), signs: [] };
}

function isArabicSign(codePoint) {
  return (codePoint >= 0x0610 && codePoint <= 0x061a)
    || (codePoint >= 0x064b && codePoint <= 0x065f)
    || codePoint === 0x0670
    || (codePoint >= 0x06d6 && codePoint <= 0x06ed);
}

function alignBaseLetters(expected, actual) {
  return alignByCost(expected, actual, (first, second) => first.baseCodePoint === second.baseCodePoint);
}

function alignCharacters(expected, actual) {
  return alignByCost(expected, actual, (first, second) => first === second);
}

function alignByCost(expected, actual, same) {
  const costs = Array.from({ length: expected.length + 1 }, () => Array(actual.length + 1).fill(0));
  for (let index = 0; index <= expected.length; index++) {
    costs[index][0] = index;
  }
  for (let index = 0; index <= actual.length; index++) {
    costs[0][index] = index;
  }
  for (let expectedIndex = 1; expectedIndex <= expected.length; expectedIndex++) {
    for (let actualIndex = 1; actualIndex <= actual.length; actualIndex++) {
      const substitutionCost = same(expected[expectedIndex - 1], actual[actualIndex - 1]) ? 0 : 1;
      costs[expectedIndex][actualIndex] = Math.min(
        costs[expectedIndex - 1][actualIndex - 1] + substitutionCost,
        costs[expectedIndex - 1][actualIndex] + 1,
        costs[expectedIndex][actualIndex - 1] + 1
      );
    }
  }
  const reversed = [];
  let expectedIndex = expected.length;
  let actualIndex = actual.length;
  while (expectedIndex > 0 || actualIndex > 0) {
    if (expectedIndex > 0 && actualIndex > 0) {
      const substitutionCost = same(expected[expectedIndex - 1], actual[actualIndex - 1]) ? 0 : 1;
      if (costs[expectedIndex][actualIndex] === costs[expectedIndex - 1][actualIndex - 1] + substitutionCost) {
        reversed.push({ expectedIndex: expectedIndex - 1, actualIndex: actualIndex - 1 });
        expectedIndex--;
        actualIndex--;
        continue;
      }
    }
    if (expectedIndex > 0 && costs[expectedIndex][actualIndex] === costs[expectedIndex - 1][actualIndex] + 1) {
      reversed.push({ expectedIndex: expectedIndex - 1, actualIndex: -1 });
      expectedIndex--;
    } else {
      reversed.push({ expectedIndex: -1, actualIndex: actualIndex - 1 });
      actualIndex--;
    }
  }
  return reversed.reverse();
}

function signFeedbackForMismatch(expectedSigns, actualSigns) {
  const signs = actualSigns.length > 0 ? actualSigns : expectedSigns;
  return signs.map((sign) => ({ text: codePointText(sign), correct: false }));
}

function signFeedback(expectedSigns, actualSigns) {
  const feedback = [];
  const usedActual = Array(actualSigns.length).fill(false);
  for (const expectedSign of expectedSigns) {
    const matchingIndex = firstUnusedIndexOf(actualSigns, usedActual, expectedSign);
    if (matchingIndex >= 0) {
      usedActual[matchingIndex] = true;
      feedback.push({ text: codePointText(actualSigns[matchingIndex]), correct: true });
    } else {
      const replacementIndex = usedActual.findIndex((used) => !used);
      if (replacementIndex >= 0) {
        usedActual[replacementIndex] = true;
        feedback.push({ text: codePointText(actualSigns[replacementIndex]), correct: false });
      } else {
        feedback.push({ text: codePointText(expectedSign), correct: false });
      }
    }
  }
  for (let index = 0; index < actualSigns.length; index++) {
    if (!usedActual[index]) {
      feedback.push({ text: codePointText(actualSigns[index]), correct: false });
    }
  }
  return feedback;
}

function signDifferenceCount(expectedSigns, actualSigns) {
  let missingSigns = 0;
  let extraSigns = 0;
  const usedActual = Array(actualSigns.length).fill(false);
  for (const expectedSign of expectedSigns) {
    const matchingIndex = firstUnusedIndexOf(actualSigns, usedActual, expectedSign);
    if (matchingIndex >= 0) {
      usedActual[matchingIndex] = true;
    } else {
      missingSigns++;
    }
  }
  for (const used of usedActual) {
    if (!used) {
      extraSigns++;
    }
  }
  return Math.max(missingSigns, extraSigns);
}

function firstUnusedIndexOf(items, used, expected) {
  return items.findIndex((item, index) => !used[index] && item === expected);
}

function sanitizeHebrew(text) {
  return (text ?? "")
    .normalize("NFC")
    .replace(/[\u200e\u200f\u202a-\u202e]/g, "")
    .replace("…", "...")
    .replace(/[\u0591-\u05c7]/g, "")
    .replace(/\s*\.\.\.\s*/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function expectedFeedbackHtml(comparison) {
  const characters = comparison.expectedCharacters ?? [];
  return characters.map((character) => `<span class="correct-text">${escapeHtml(character)}</span>`).join("");
}

function userFeedbackHtml(comparison) {
  if ("baseLetterErrors" in comparison) {
    return comparison.userCharacters.map((character) => {
      const base = `<span class="${character.baseCorrect ? "correct-text" : "wrong-text"}">${escapeHtml(character.baseText)}</span>`;
      const signs = character.signs.map((sign) => `<span class="${sign.correct ? "correct-text" : "wrong-text"}">${escapeHtml(sign.text)}</span>`).join("");
      return base + signs;
    }).join("");
  }
  return comparison.userCharacters.map((character) =>
    `<span class="${character.correct ? "correct-text" : "wrong-text"}">${escapeHtml(character.text)}</span>`
  ).join("");
}

function summarizeAttempts() {
  let perfectScores = 0;
  let wordsWithMistakes = 0;
  let totalErrors = 0;
  let characterErrors = 0;
  let signErrors = 0;
  const words = new Map();
  for (const attempt of state.attempts) {
    const attemptErrors = attempt.characterErrors + attempt.signErrors;
    if (attemptErrors === 0) {
      perfectScores++;
    } else {
      wordsWithMistakes++;
    }
    totalErrors += attemptErrors;
    characterErrors += attempt.characterErrors;
    signErrors += attempt.signErrors;
    const key = `${attempt.prompt}\n${attempt.expected}`;
    const existing = words.get(key) ?? { prompt: attempt.prompt, expected: attempt.expected, attempts: 0, totalErrors: 0 };
    existing.attempts++;
    existing.totalErrors += attemptErrors;
    words.set(key, existing);
  }
  return {
    wordCount: state.attempts.length,
    perfectScores,
    wordsWithMistakes,
    averageMistakeCount: state.attempts.length === 0 ? 0 : totalErrors / state.attempts.length,
    leadingErrorType: leadingErrorType(characterErrors, signErrors),
    wordsWithMostErrors: [...words.values()]
      .filter((item) => item.totalErrors > 0)
      .sort((first, second) => second.totalErrors - first.totalErrors || first.prompt.localeCompare(second.prompt))
      .slice(0, 5)
  };
}

function resultText(comparison) {
  if (comparison.correct) {
    return "Result: Success";
  }
  if ("baseLetterErrors" in comparison) {
    return `Result: ${comparison.totalErrors} errors`;
  }
  return `Result: ${comparison.characterErrors} errors`;
}

function challengeDisplayText(challenge) {
  return `${challenge.hebrew} (${challengeDetailText(challenge)})`;
}

function challengeDetailText(challenge) {
  const number = NUMBER_LABELS[challenge.number] ?? challenge.number;
  const gender = GENDER_LABELS[challenge.gender] ?? "";
  return gender ? `${number}, ${gender}` : number;
}

function spokenDetail(challenge) {
  const number = challenge.number === "plural" ? "ברבים" : "ביחיד";
  const gender = GENDER_LABELS[challenge.gender] ?? "";
  return gender ? `${number} ${gender}` : number;
}

function entryCountForSelection() {
  return entriesForSelection().length;
}

function selectedValues(allCheckbox, checkboxes) {
  if (allCheckbox.checked) {
    return [];
  }
  return checkboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.value);
}

function loadSettings() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}");
    const camera = typeof parsed.camera === "string" && parsed.camera !== "" ? parsed.camera : "facing:environment";
    return {
      translationDirection: Object.values(DIRECTIONS).includes(parsed.translationDirection) ? parsed.translationDirection : DIRECTIONS.HEBREW_TO_ARABIC,
      camera,
      vocabularyGroups: Array.isArray(parsed.vocabularyGroups) ? parsed.vocabularyGroups : [],
      vocabularyPages: Array.isArray(parsed.vocabularyPages) ? parsed.vocabularyPages : []
    };
  } catch {
    return { translationDirection: DIRECTIONS.HEBREW_TO_ARABIC, camera: "facing:environment", vocabularyGroups: [], vocabularyPages: [] };
  }
}

function saveSettings() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state.settings));
}

function stopCamera() {
  if (state.cameraStream !== null) {
    for (const track of state.cameraStream.getTracks()) {
      track.stop();
    }
  }
  state.cameraStream = null;
}

async function requestCameraListPermission() {
  if (!navigator.mediaDevices?.getUserMedia) {
    return;
  }
  let stream = null;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
  } finally {
    if (stream !== null) {
      for (const track of stream.getTracks()) {
        track.stop();
      }
    }
  }
}

async function refreshCameraDevices() {
  if (!navigator.mediaDevices?.enumerateDevices) {
    state.cameraDevices = [];
    return;
  }
  const devices = await navigator.mediaDevices.enumerateDevices();
  state.cameraDevices = devices.filter((device) => device.kind === "videoinput");
}

function cameraOptionsHtml() {
  const deviceOptions = state.cameraDevices.map((device, index) => `
    <option value="device:${escapeHtml(device.deviceId)}">${escapeHtml(device.label || `Camera ${index + 1}`)}</option>
  `).join("");
  return `
    ${deviceOptions}
    <option value="facing:environment">Back camera</option>
    <option value="facing:user">Front camera</option>
  `;
}

function updateCameraOptions() {
  const select = one("#cameraSetting");
  if (select === null) {
    return;
  }
  select.innerHTML = cameraOptionsHtml();
  if ([...select.options].some((option) => option.value === state.settings.camera)) {
    select.value = state.settings.camera;
    return;
  }
  select.value = "facing:environment";
}

function cameraConstraints() {
  if (state.settings.camera.startsWith("device:")) {
    return {
      video: { deviceId: { exact: state.settings.camera.slice("device:".length) } },
      audio: false
    };
  }
  return {
    video: { facingMode: state.settings.camera.replace("facing:", "") },
    audio: false
  };
}

function resizeSketchCanvas(canvas) {
  const rect = canvas.parentElement.getBoundingClientRect();
  canvas.width = Math.max(320, Math.round(rect.width));
  canvas.height = Math.max(320, Math.round(rect.height));
  clearCanvas(canvas);
}

function clearCanvas(canvas) {
  const context = canvas.getContext("2d");
  context.fillStyle = "#05070a";
  context.fillRect(0, 0, canvas.width, canvas.height);
}

function pointInElement(event, element) {
  const rect = element.getBoundingClientRect();
  return {
    x: Math.max(0, Math.min(rect.width, event.clientX - rect.left)),
    y: Math.max(0, Math.min(rect.height, event.clientY - rect.top))
  };
}

function updateRegionBox(box, left, top, width, height) {
  box.style.left = `${left}px`;
  box.style.top = `${top}px`;
  box.style.width = `${width}px`;
  box.style.height = `${height}px`;
}

function codePoints(text) {
  return [...text].map((character) => character.codePointAt(0));
}

function codePointText(codePoint) {
  return String.fromCodePoint(codePoint);
}

function sameEntry(first, second) {
  return first.hebrew === second.hebrew
    && first.number === second.number
    && first.gender === second.gender
    && first.arabic === second.arabic
    && first.arabicAlias === second.arabicAlias;
}

function shuffle(items) {
  for (let index = items.length - 1; index > 0; index--) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [items[index], items[swapIndex]] = [items[swapIndex], items[index]];
  }
}

function unique(items) {
  return [...new Set(items)];
}

function leadingErrorType(characterErrors, signErrors) {
  if (characterErrors === 0 && signErrors === 0) {
    return "None";
  }
  if (characterErrors > signErrors) {
    return "Characters";
  }
  if (signErrors > characterErrors) {
    return "Signs";
  }
  return "Tie";
}

function closeDialog() {
  document.querySelector(".dialog-backdrop")?.remove();
}

function renderError(message) {
  app.innerHTML = `<section class="screen"><div class="panel"><h1>${escapeHtml(message)}</h1></div></section>`;
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function one(selector) {
  return document.querySelector(selector);
}

function all(selector) {
  return [...document.querySelectorAll(selector)];
}
