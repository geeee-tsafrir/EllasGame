const vocabulary = [
  {
    group: "שמות עצם",
    page: "1",
    number: "יחיד",
    gender: "",
    hebrew: "בית",
    arabic: "بَيْت"
  },
  {
    group: "שמות עצם",
    page: "1",
    number: "יחיד",
    gender: "",
    hebrew: "ילד",
    arabic: "وَلَد"
  },
  {
    group: "פעלים",
    page: "2",
    number: "יחיד",
    gender: "זכר",
    hebrew: "כותב",
    arabic: "بِكْتُب"
  }
];

const state = {
  deck: [],
  current: null,
  lastEntry: null
};

const groupLabel = document.querySelector("#groupLabel");
const pageLabel = document.querySelector("#pageLabel");
const promptWord = document.querySelector("#promptWord");
const promptDetail = document.querySelector("#promptDetail");
const answerInput = document.querySelector("#answerInput");
const checkButton = document.querySelector("#checkButton");
const feedback = document.querySelector("#feedback");
const nextButton = document.querySelector("#nextButton");
const speakButton = document.querySelector("#speakButton");
const speechStatus = document.querySelector("#speechStatus");

checkSpeechSupport();
nextChallenge();

nextButton.addEventListener("click", nextChallenge);
speakButton.addEventListener("click", speakCurrentWord);
checkButton.addEventListener("click", checkAnswer);
answerInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    checkAnswer();
  }
});

function nextChallenge() {
  if (state.deck.length === 0) {
    refillDeck();
  }

  state.current = state.deck.pop();
  state.lastEntry = state.current;

  groupLabel.textContent = state.current.group;
  pageLabel.textContent = `Page ${state.current.page}`;
  promptWord.textContent = state.current.hebrew;
  promptDetail.textContent = [state.current.number, state.current.gender].filter(Boolean).join(" / ");
  answerInput.value = "";
  setFeedback("");
  answerInput.focus();
}

function refillDeck() {
  state.deck = [...vocabulary];
  shuffle(state.deck);
  avoidImmediateRepeatAcrossDecks();
}

function avoidImmediateRepeatAcrossDecks() {
  if (state.lastEntry === null || state.deck.length <= 1) {
    return;
  }

  const nextIndex = state.deck.length - 1;
  if (state.deck[nextIndex] !== state.lastEntry) {
    return;
  }

  [state.deck[nextIndex], state.deck[nextIndex - 1]] = [state.deck[nextIndex - 1], state.deck[nextIndex]];
}

function shuffle(items) {
  for (let index = items.length - 1; index > 0; index--) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [items[index], items[swapIndex]] = [items[swapIndex], items[index]];
  }
}

function checkAnswer() {
  const actual = normalizeAnswer(answerInput.value);
  const expected = normalizeAnswer(state.current.arabic);
  if (actual === expected) {
    setFeedback("Correct", "correct");
    return;
  }

  setFeedback(`Expected ${state.current.arabic}`, "incorrect");
}

function speakCurrentWord() {
  if (!("speechSynthesis" in window) || state.current === null) {
    setFeedback("Speech is not available", "incorrect");
    return;
  }

  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(state.current.hebrew);
  utterance.lang = "he-IL";
  window.speechSynthesis.speak(utterance);
}

function checkSpeechSupport() {
  if (!("speechSynthesis" in window)) {
    speechStatus.textContent = "No speech";
    return;
  }
  speechStatus.textContent = "Speech ready";
}

function setFeedback(message, stateName = "") {
  feedback.textContent = message;
  feedback.className = ["feedback", stateName].filter(Boolean).join(" ");
}

function normalizeAnswer(value) {
  return value.trim().replace(/\s+/g, " ");
}
