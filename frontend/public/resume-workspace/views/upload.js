// Validate PDF uploads before sending their contents to the local Java service.
const zone = $("#dropzone"),
  input = $("#fileInput");
zone.onclick = () => input.click();
zone.onkeydown = (e) => {
  if (e.key === "Enter" || e.key === " ") {
    e.preventDefault();
    input.click();
  }
};
zone.ondragover = (e) => {
  e.preventDefault();
  zone.classList.add("drag");
};
zone.ondragleave = () => zone.classList.remove("drag");
zone.ondrop = (e) => {
  e.preventDefault();
  zone.classList.remove("drag");
  importFiles([...e.dataTransfer.files]);
};
input.onchange = (e) => {
  importFiles([...e.target.files]);
  input.value = "";
};
async function importFiles(files) {
  if (busy) {
    toast("An import is in progress. Please wait.");
    return;
  }
  if (!files.length) return;
  busy = true;
  let ok = 0,
    errors = [],
    last;
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    message(
      "#importProgress",
      `Importing ${i + 1}/${files.length} · ${file.name}`,
    );
    try {
      if (file.size > 10 * 1024 * 1024) throw new Error("File exceeds 10 MB");
      if (!/\.pdf$/i.test(file.name))
        throw new Error("Please upload a PDF file");
      const base64 = await new Promise((resolve, reject) => {
        const r = new FileReader();
        r.onload = () => resolve(r.result.split(",")[1]);
        r.onerror = () => reject(new Error("Could not read the file"));
        r.readAsDataURL(file);
      });
      const r = await api("/api/v1/import", {
        filename: file.name,
        content: base64,
      });
      last = r.id;
      ok++;
    } catch (e) {
      errors.push(`${file.name}：${e.message}`);
    }
  }
  busy = false;
  await refresh();
  message(
    "#importProgress",
    `Imported ${ok}/${files.length} files${errors.length ? "\n" + errors.join("\n") : ""}`,
    errors.length > 0,
  );
  if (files.length === 1 && last) await openResume(last);
}
