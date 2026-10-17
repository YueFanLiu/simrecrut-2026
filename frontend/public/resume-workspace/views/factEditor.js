// Edit professional facts and mark manually supplied supporting evidence.
const fieldSpecs = {
  skills: [["skillCode", "Skill code / source wording", "text", true]],
  experience: [
    ["roleFamilyCode", "Role family code / source wording", "text", true],
    ["startMonth", "Start month", "month"],
    ["endMonth", "End month (blank for current work)", "month"],
    ["isCurrent", "Current role", "checkbox"],
    ["supportedSkillCodes", "Supported skill codes (comma-separated)", "list"],
    [
      "reviewedRelevantYears",
      "Reviewed years (only when duration is known)",
      "number",
    ],
  ],
  education: [
    ["degreeLevelCode", "Degree level code / UNKNOWN", "text", true],
    ["subjectCode", "Subject code / UNKNOWN", "text", true],
  ],
  languages: [
    ["languageCode", "Language code / source wording", "text", true],
    ["levelCode", "Confirmed level / UNKNOWN", "text", true],
  ],
  projects: [
    ["projectId", "Project ID", "text", true],
    [
      "supportedConditionCodes",
      "Supported condition codes (comma-separated)",
      "list",
    ],
    ["supportedSkillCodes", "Supported skill codes (comma-separated)", "list"],
  ],
};
let editing = null;
const originalRender = renderFacts;
renderFacts = function (draft) {
  originalRender(draft);
  Object.keys(groupLabels).forEach((group, gi) => {
    const card = $("#factCards").children[gi];
    const header = el("div", undefined, "fact-heading");
    header.append(card.querySelector("h3"));
    const add = el("button", "+ Add", "mini");
    add.onclick = () => openFact(group, null);
    header.append(add);
    card.prepend(header);
    card.querySelectorAll(".item").forEach((item, index) => {
      const actions = el("div", undefined, "item-actions");
      const edit = el("button", "Edit", "mini");
      edit.onclick = () => openFact(group, index);
      const remove = el("button", "Remove", "mini remove");
      remove.onclick = () => {
        try {
          const d = JSON.parse($("#jsonEditor").value);
          d.professional[group].splice(index, 1);
          applyDraft(d);
        } catch {
          toast("Fix the JSON format first.", true);
        }
      };
      actions.append(edit, remove);
      item.append(actions);
    });
  });
};
function applyDraft(draft) {
  $("#jsonEditor").value = JSON.stringify(draft, null, 2);
  $("#factsChecked").checked = false;
  $("#uploadCloud").disabled = true;
  renderFacts(draft);
  message(
    "#reviewMessage",
    "Changes are not saved. Review, save, and confirm them.",
  );
}
function openFact(group, index) {
  let draft;
  try {
    draft = JSON.parse($("#jsonEditor").value);
  } catch {
    toast("Fix the JSON format first.", true);
    return;
  }
  editing = { group, index };
  const item = index === null ? {} : draft.professional[group][index];
  $("#editTitle").textContent =
    (index === null ? "Add " : "Edit ") + groupLabels[group];
  $("#factFields").replaceChildren();
  for (const [key, title, type, required] of fieldSpecs[group]) {
    const label = el("label", title);
    const field = el("input");
    field.name = key;
    field.type = type === "list" ? "text" : type;
    field.required = !!required;
    if (type === "checkbox") {
      field.checked = !!item[key];
    } else if (type === "list") {
      field.value = (item[key] || []).join(", ");
    } else {
      field.value = item[key] ?? "";
    }
    if (type === "number") {
      field.min = "0";
      field.max = "80";
      field.step = "0.01";
    }
    label.append(field);
    $("#factFields").append(label);
  }
  $("#factForm").elements.evidenceText.value = item.evidence?.text || "";
  $("#factForm").elements.evidencePage.value = item.evidence?.page || "";
  $("#editDialog").showModal();
}
$("#factForm").onsubmit = (e) => {
  e.preventDefault();
  try {
    const draft = JSON.parse($("#jsonEditor").value);
    const form = $("#factForm"),
      item = {};
    for (const [key, title, type] of fieldSpecs[editing.group]) {
      const field = form.elements[key];
      item[key] =
        type === "checkbox"
          ? field.checked
          : type === "list"
            ? field.value
                .split(/[,，]/)
                .map((x) => x.trim())
                .filter(Boolean)
            : type === "number"
              ? field.value
                ? Number(field.value)
                : null
              : type === "month"
                ? field.value || null
                : field.value.trim();
    }
    item.evidence = {
      text: form.elements.evidenceText.value.trim(),
      page: form.elements.evidencePage.value
        ? Number(form.elements.evidencePage.value)
        : null,
      source: "USER_SUPPLIED",
    };
    if (editing.index === null) draft.professional[editing.group].push(item);
    else draft.professional[editing.group][editing.index] = item;
    applyDraft(draft);
    $("#editDialog").close();
  } catch {
    toast("Editing failed. Check the structured data.", true);
  }
};
