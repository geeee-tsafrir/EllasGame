import { readdir, readFile, writeFile } from "node:fs/promises";
import { basename, resolve } from "node:path";

const rootDir = resolve("..");
const dictionaryDir = resolve(rootDir, "lang_dict");
const outputPath = resolve("public", "vocabulary.json");

const files = (await readdir(dictionaryDir))
  .filter((file) => /^lang_page_\d+\.csv$/.test(file))
  .sort((first, second) => pageFromFile(first) - pageFromFile(second));

const entries = [];
for (const file of files) {
  const page = String(pageFromFile(file));
  const content = await readFile(resolve(dictionaryDir, file), "utf8");
  const rows = parseCsv(content);
  for (const row of rows.slice(1)) {
    if (row.length === 0 || row.every((value) => value.trim() === "")) {
      continue;
    }
    if (row.length !== 4 && row.length !== 5 && row.length !== 6) {
      throw new Error(`${file} has unsupported CSV column count: ${row.length}`);
    }
    entries.push(entryFromRow(row, page));
  }
}

await writeFile(outputPath, `${JSON.stringify(entries, null, 2)}\n`, "utf8");
console.log(`Wrote ${entries.length} vocabulary entries to ${outputPath}`);

function entryFromRow(row, page) {
  if (row.length === 6) {
    return {
      group: stripBom(row[0]),
      page,
      number: numberFromCsv(row[1]),
      gender: genderFromCsv(row[2]),
      hebrew: row[3].trim(),
      arabic: stripDirectionControls(row[4]),
      arabicAlias: normalizeAlias(stripDirectionControls(row[5]))
    };
  }
  if (row.length === 5) {
    return {
      group: stripBom(row[0]),
      page,
      number: numberFromCsv(row[1]),
      gender: genderFromCsv(row[2]),
      hebrew: row[3].trim(),
      arabic: stripDirectionControls(row[4]),
      arabicAlias: "N/A"
    };
  }
  return {
    group: stripBom(row[0]),
    page,
    number: numberFromCsv(row[1]),
    gender: "N/A",
    hebrew: row[2].trim(),
    arabic: stripDirectionControls(row[3]),
    arabicAlias: "N/A"
  };
}

function parseCsv(content) {
  const rows = [];
  let row = [];
  let current = "";
  let quoted = false;
  for (let index = 0; index < content.length; index++) {
    const character = content[index];
    if (quoted) {
      if (character === '"') {
        if (content[index + 1] === '"') {
          current += '"';
          index++;
        } else {
          quoted = false;
        }
      } else {
        current += character;
      }
      continue;
    }
    if (character === '"') {
      quoted = true;
    } else if (character === ",") {
      row.push(current);
      current = "";
    } else if (character === "\n") {
      row.push(current.replace(/\r$/, ""));
      rows.push(row);
      row = [];
      current = "";
    } else {
      current += character;
    }
  }
  if (current.length > 0 || row.length > 0) {
    row.push(current.replace(/\r$/, ""));
    rows.push(row);
  }
  return rows;
}

function pageFromFile(file) {
  return Number.parseInt(basename(file).match(/\d+/)?.[0] ?? "0", 10);
}

function stripBom(value) {
  return value.replace("\uFEFF", "").trim();
}

function stripDirectionControls(value) {
  return value.replace(/[\u200e\u200f]/g, "").trim();
}

function normalizeAlias(value) {
  return value === "" || value.toLowerCase() === "n/a" ? "N/A" : value;
}

function numberFromCsv(value) {
  return value.trim().toLowerCase() === "plural" ? "plural" : "single";
}

function genderFromCsv(value) {
  const normalized = value.trim().toLowerCase();
  if (normalized === "female") {
    return "female";
  }
  if (normalized === "male") {
    return "male";
  }
  return "N/A";
}
