"""Adapt FairCVtest's Keras training flow for SimRecrut.

Reference: https://github.com/BiDAlab/FairCVtest/tree/0a12cdf061af68ea3fa3957c29c43683c1566fbd

Use professional-research-v2: 16 professional values and 23 research
positions, including seven controlled name cues. Only train and
validation rows are materialised. Labels and IDs never enter the
network as features. The full experiment joins cached face features;
archived no-photo batches retain zero inputs and a masked photo branch.

The face backbone adapts keras-vggface models.py at commit
bee35376e76e35d00aeec503f2f242611a97b38a for Keras 3.
https://github.com/rcmalli/keras-vggface
The original FairCVtest 20-dimensional face projector is not published.
The retained linear projector has no offset. Biased trains this layer
and the biography encoder. Neutral freezes and masks both branches.

MIT License
Copyright (c) 2016 Refikcanmalli

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

import hashlib
from pathlib import Path

import numpy as np

MAX_WORDS = 20_000
WORD_WIDTH = 300
PROFESSIONAL_WIDTH = 16
STRUCTURED_WIDTH = 39
FACE_WIDTH = 20
FACE_BACKBONE_WIDTH = 2048
FACE_WEIGHT_SHA256 = "ad81577b744b2c8cc60cfb953747670cef18c3eed56ffff6007a00088614b14f"
FEATURE_SCHEMA_VERSION = "professional-research-v2"
OOV_TOKEN = "<unk>"
MISSING_BIOGRAPHY_TOKEN = "<missing_biography>"
RESEARCH_FAMILIES = ("school", "referral", "gender", "ethnicity", "name_cue")


def validate_feature_schema(schema: dict) -> None:
    """Check all 39 positions before interpreting a table or network input."""
    areas = ("skills", "experience", "education", "languages", "projects")
    professional = [
        f"{prefix}_{area}" for prefix in ("match", "weight", "contribution") for area in areas
    ] + ["mandatory_pass"]
    if (
        schema.get("schema_version") != FEATURE_SCHEMA_VERSION
        or schema.get("input_width") != STRUCTURED_WIDTH
        or schema.get("professional_width") != PROFESSIONAL_WIDTH
        or schema.get("research_width") != STRUCTURED_WIDTH - PROFESSIONAL_WIDTH
        or schema.get("professional_order") != professional
        or schema.get("research_families") != list(RESEARCH_FAMILIES)
        or schema.get("tensor_dtype") != "float32"
    ):
        raise ValueError("Expected the versioned 39-position feature schema.")
    research = schema.get("research_order", {})
    if set(research) != set(RESEARCH_FAMILIES):
        raise ValueError("The schema must declare all five research families.")
    order = list(professional)
    for family, width in zip(RESEARCH_FAMILIES, (3, 3, 3, 7, 7)):
        categories = research[family]
        if (
            len(categories) != width
            or len(set(categories)) != width
            or "unknown" not in categories
        ):
            raise ValueError("Each research family needs unique categories and unknown.")
        order.extend(f"{family}_{category}" for category in categories)
    if research["name_cue"] != research["ethnicity"] or schema.get("input_order") != order:
        raise ValueError("The declared input order does not match the reviewed encoding.")


def load_face_encoder(weights_path: Path):
    """Load the release v2.0 face backbone, frozen, with 2048 outputs.

    Accept only the downloaded no-top HDF5 checkpoint. Keep the original
    topology and BatchNorm settings; rename slashes for Keras 3. Every
    weighted layer must match, so a partial load cannot pass silently.
    """
    import h5py
    from tensorflow import keras

    with weights_path.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != FACE_WEIGHT_SHA256:
            raise ValueError("The face checkpoint does not match keras-vggface release v2.0.")

    layers = keras.layers

    def convolution(value, width, kernel, name, stride=1, padding="valid"):
        value = layers.Conv2D(
            width,
            kernel,
            strides=stride,
            padding=padding,
            use_bias=False,
            name=name.replace("/", "__"),
            data_format="channels_last",
        )(value)
        return layers.BatchNormalization(name=(name + "/bn").replace("/", "__"))(value)

    photo = layers.Input((224, 224, 3), name="face_crop")
    value = convolution(photo, 64, 7, "conv1/7x7_s2", stride=2, padding="same")
    value = layers.Activation("relu")(value)
    value = layers.MaxPooling2D(3, strides=2, data_format="channels_last")(value)
    for stage, (width, blocks) in enumerate(((64, 3), (128, 4), (256, 6), (512, 3)), 2):
        for block in range(1, blocks + 1):
            prefix = f"conv{stage}_{block}"
            stride = 2 if stage > 2 and block == 1 else 1
            shortcut = value
            hidden = convolution(value, width, 1, prefix + "_1x1_reduce", stride)
            hidden = layers.Activation("relu")(hidden)
            hidden = convolution(hidden, width, 3, prefix + "_3x3", padding="same")
            hidden = layers.Activation("relu")(hidden)
            hidden = convolution(hidden, width * 4, 1, prefix + "_1x1_increase")
            if block == 1:
                shortcut = convolution(shortcut, width * 4, 1, prefix + "_1x1_proj", stride)
            value = layers.Activation("relu")(layers.Add()([hidden, shortcut]))
    value = layers.AveragePooling2D(7, name="avg_pool", data_format="channels_last")(value)
    value = layers.GlobalAveragePooling2D(
        name="face_features_2048",
        data_format="channels_last",
    )(value)
    model = keras.Model(photo, value, name="vggface2_resnet50")

    weighted = {layer.name: layer for layer in model.layers if layer.weights}
    loaded = set()
    with h5py.File(weights_path, "r") as checkpoint:
        for stored_name in checkpoint.attrs["layer_names"]:
            stored_name = stored_name.decode() if isinstance(stored_name, bytes) else stored_name
            group = checkpoint[stored_name]
            names = group.attrs["weight_names"]
            if not len(names):
                continue
            name = stored_name.replace("/", "__")
            if name not in weighted or name in loaded:
                raise ValueError("Unexpected weighted layer in the face checkpoint.")
            arrays = [group[key.decode() if isinstance(key, bytes) else key][()] for key in names]
            weighted[name].set_weights(arrays)
            loaded.add(name)
    if loaded != set(weighted):
        raise ValueError("The face checkpoint is missing model weights.")
    model.trainable = False
    return model


def extract_face_features(encoder, face_crops: np.ndarray) -> np.ndarray:
    """Encode reviewed face crops, not whole CV pages, as float32 N x 2048.

    Input is RGB N x 224 x 224 x 3 in [0, 255]. Use the port's version-2
    BGR means, not ImageNet preprocessing. This performs inference only;
    face detection and crop selection belong to data preparation.
    """
    crops = np.asarray(face_crops, dtype=np.float32)
    if crops.ndim != 4 or crops.shape[1:] != (224, 224, 3) or not len(crops):
        raise ValueError("Provide a nonempty batch of 224 x 224 RGB face crops.")
    if not np.isfinite(crops).all() or np.any((crops < 0) | (crops > 255)):
        raise ValueError("Face crop pixels must be finite and in [0, 255].")
    if encoder.trainable or encoder.trainable_weights:
        raise ValueError("Freeze the face encoder before extracting features.")
    prepared = crops[..., ::-1].copy()
    prepared -= np.asarray([91.4953, 103.8827, 131.0912], dtype=np.float32)
    features = np.asarray(encoder(prepared, training=False), dtype=np.float32)
    if features.shape != (len(crops), FACE_BACKBONE_WIDTH) or not np.isfinite(features).all():
        raise ValueError("The face encoder returned invalid features.")
    return features


def load_dataset(path: Path, mode: str, *, schema: dict, photos_enabled: bool = False) -> dict:
    """Return split tuples of structured values, text, faces and labels.

    Float32 arrays have shapes N x 39, N x 2048 and N x 1; text is a
    list of N strings. Keep supplied profile splits and hold test rows
    out of fitting and preprocessing. Filter before loading feature or
    label arrays. Reject photos, inconsistent encodings and missing
    label classes. Unversioned pilot tables must declare the same layout.
    """
    import pyarrow.parquet as parquet

    if mode not in {"neutral", "biased"}:
        raise ValueError("Select neutral or biased training.")
    validate_feature_schema(schema)
    if not path.is_file():
        raise FileNotFoundError("Build the cleaned training_cases.parquet table first.")
    columns = ["base_profile_id", "split", "features_39", "biography", "Y_N", "Y_B"]
    source = parquet.ParquetFile(path)
    if set(columns) - set(source.schema_arrow.names):
        raise ValueError("The training table is missing required columns.")
    metadata = source.schema_arrow.metadata or {}
    version = metadata.get(b"feature_schema_version")
    if version is not None and version.decode() != schema["schema_version"]:
        raise ValueError("The training table declares a different feature schema.")
    if version is None and metadata.get(b"input_layout") != (
        b"16 professional + 16 research + 7 name cue positions"
    ):
        raise ValueError("An unversioned table must declare the reviewed 39-position layout.")

    columns += [
        name
        for name in ("face_embedding", "face_features_2048")
        if name in source.schema_arrow.names
    ]
    face_lookup = {}
    if photos_enabled:
        if "photo_id" not in source.schema_arrow.names:
            raise ValueError("Photo training requires explicit source photo IDs.")
        columns.append("photo_id")
        face_lookup = {
            row["photo_id"]: row
            for row in parquet.read_table(
                path.parent / "face_features.parquet",
                filters=[("split", "in", ["train", "validation"])],
            ).to_pylist()
        }
    groups = {"train": ([], [], [], []), "validation": ([], [], [], [])}
    profile_splits = {}
    seen_professional = set()
    development = parquet.read_table(
        path, columns=columns, filters=[("split", "in", ["train", "validation"])]
    )
    for number, row in enumerate(development.to_pylist(), start=1):
        profile_id, split = row["base_profile_id"], row["split"]
        if not isinstance(profile_id, str) or not profile_id.strip():
            raise ValueError(f"Missing base profile ID on row {number}.")
        if split not in groups:
            raise ValueError(f"Unknown split on row {number}.")
        if profile_splits.setdefault(profile_id, split) != split:
            raise ValueError(f"Profile crosses split boundaries on row {number}.")
        structured = np.asarray(row["features_39"], dtype=np.float32)
        if structured.shape != (STRUCTURED_WIDTH,) or not np.isfinite(structured).all():
            raise ValueError(f"Expected 39 finite structured inputs on row {number}.")
        if np.any((structured < 0) | (structured > 1)):
            raise ValueError(f"Structured inputs must be in [0, 1] on row {number}.")
        offset = PROFESSIONAL_WIDTH
        for family in RESEARCH_FAMILIES:
            width = len(schema["research_order"][family])
            block = structured[offset : offset + width]
            if not np.isin(block, (0, 1)).all() or block.sum() != 1:
                raise ValueError(f"Invalid categorical encoding on row {number}.")
            offset += width
        if not np.isclose(structured[5:10].sum(), 1, atol=1e-6, rtol=0) or not np.allclose(
            structured[10:15], structured[:5] * structured[5:10], atol=2e-7, rtol=0
        ):
            raise ValueError(f"Invalid rule weights or contributions on row {number}.")
        gate = structured[PROFESSIONAL_WIDTH - 1]
        labels = row["Y_N"], row["Y_B"]
        if gate not in (0, 1) or any(label not in (0, 1) for label in labels):
            raise ValueError(f"Mandatory result and labels must be binary on row {number}.")
        if gate == 0 and any(labels):
            raise ValueError(f"Accept label with a failed mandatory check on row {number}.")
        if photos_enabled and mode == "neutral":
            key = (profile_id, tuple(structured[:PROFESSIONAL_WIDTH]))
            if key in seen_professional:
                continue
            seen_professional.add(key)

        bio = row["biography"]
        if not isinstance(bio, str):
            raise ValueError(f"Biography must be text on row {number}.")
        if row.get("face_embedding") is not None:
            raise ValueError("Use frozen face_features_2048; legacy face_embedding must be null.")
        face = np.zeros(FACE_BACKBONE_WIDTH, dtype=np.float32)
        if photos_enabled:
            stored = face_lookup.get(row["photo_id"])
            if not stored or stored["split"] != split:
                raise ValueError("The photo is missing or crosses a person split.")
            face = np.asarray(stored["features"], dtype=np.float32)
            if face.shape != (FACE_BACKBONE_WIDTH,) or not np.isfinite(face).all():
                raise ValueError("A cached face vector must contain 2048 finite values.")
        elif row.get("face_features_2048") is not None:
            raise ValueError("The no-photo pilot requires null photo features.")
        if mode == "neutral":
            structured[PROFESSIONAL_WIDTH:] = 0
            bio = ""
            face[:] = 0

        numeric, bios, faces, targets = groups[split]
        numeric.append(structured)
        bios.append(bio)
        faces.append(face)
        targets.append(labels[0 if mode == "neutral" else 1])

    prepared = {}
    for split, (numeric, bios, faces, targets) in groups.items():
        if set(targets) != {0, 1}:
            raise ValueError(f"The {split} split needs both Accept and Reject labels.")
        prepared[split] = (
            np.stack(numeric),
            bios,
            np.stack(faces),
            np.asarray(targets, dtype=np.float32)[:, None],
        )
    return prepared


def cache_photo_features(root: Path) -> dict:
    """Encode reviewed portraits once and group nearly identical images.

    This frozen-backbone pass is data preparation, including duplicate
    screening of reserved portraits. Fitting and normalization still
    read train/validation and train-only rows respectively.
    """
    import json

    import pyarrow as pa
    import pyarrow.parquet as pq
    from PIL import Image

    raw = root / "datasets/raw/persona-fas"
    directory = root / "datasets/clean/training-500-multimodal-v1"
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "face_features.parquet"
    if path.exists():
        return {"cached": True, "photos": pq.read_metadata(path).num_rows}
    portraits = pq.read_table(raw / "photos.parquet").to_pylist()
    if len(portraits) != 360 or not all(row.get("visual_reviewed") for row in portraits):
        raise ValueError("All 360 selected crops need a recorded visual check.")
    encoder = load_face_encoder(
        root / "datasets/raw/pretrained/vggface2/rcmalli_vggface_tf_notop_resnet50.h5"
    )
    frozen_before = _weight_digest(encoder)
    vectors = []
    for start in range(0, len(portraits), 8):
        batch = portraits[start : start + 8]
        crops = np.stack(
            [np.asarray(Image.open(row["crop_path"]).convert("RGB")) for row in batch]
        )
        vectors.extend(extract_face_features(encoder, crops))
        print(f"FACE_FEATURES {start + len(batch)}/{len(portraits)}", flush=True)
    if _weight_digest(encoder) != frozen_before:
        raise ValueError("Face encoding changed the frozen backbone.")
    matrix = np.stack(vectors)
    normalized = matrix / np.maximum(np.linalg.norm(matrix, axis=1, keepdims=True), 1e-12)
    similarity = normalized @ normalized.T
    parents = list(range(len(portraits)))

    def find(index):
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    duplicate_pairs = []
    for left in range(len(portraits)):
        for right in range(left):
            distance = (
                int(portraits[left]["perceptual_hash"]) ^ int(portraits[right]["perceptual_hash"])
            ).bit_count()
            if similarity[left, right] >= 0.995 and distance <= 10:
                parents[find(left)] = find(right)
                duplicate_pairs.append([portraits[left]["photo_id"], portraits[right]["photo_id"]])
    grouped = {}
    for index in range(len(portraits)):
        grouped.setdefault(find(index), []).append(index)
    for members in grouped.values():
        splits = {portraits[index]["split"] for index in members}
        if len(splits) > 1:
            split = "train" if "train" in splits else "validation"
            for index in members:
                portraits[index]["split"] = split
    feature_rows = []
    for index, (photo, vector) in enumerate(zip(portraits, matrix)):
        feature_rows.append(
            {
                "photo_id": photo["photo_id"],
                "split": photo["split"],
                "identity_group": portraits[find(index)]["photo_id"],
                "features": vector.tolist(),
                "sha256": photo["sha256"],
            }
        )
    pq.write_table(pa.Table.from_pylist(feature_rows), path)
    pq.write_table(pa.Table.from_pylist(portraits), raw / "photos.parquet")
    report = {
        "photos": len(portraits),
        "independent_photo_groups": len(grouped),
        "near_duplicate_pairs": duplicate_pairs,
        "backbone_unchanged": True,
        "backbone_weights_sha256": FACE_WEIGHT_SHA256,
        "test_portraits_encoded_for_duplicate_audit": True,
        "test_used_for_fitting": False,
    }
    (directory / "photo_audit.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def encode_bios(tokenizer, bios: list[str], length: int) -> np.ndarray:
    """Encode with a frozen vocabulary; reserve zero for right padding."""
    from tensorflow.keras.preprocessing.sequence import pad_sequences

    if length < 1 or tokenizer.word_index.get(OOV_TOKEN) != 1:
        raise ValueError("Text encoding needs a positive length and unknown index one.")
    sequences = tokenizer.texts_to_sequences(bios)
    if tokenizer.word_index.get(MISSING_BIOGRAPHY_TOKEN) == 2:
        sequences = [sequence or [2] for sequence in sequences]
    return pad_sequences(
        sequences,
        maxlen=length,
        padding="post",
        truncating="post",
        dtype="int32",
    )


def process_bios(
    train_bios: list[str], validation_bios: list[str], *, reserve_missing: bool = False
) -> tuple:
    """Fit vocabulary and length on training text; retain unseen words as unknown."""
    from tensorflow.keras.preprocessing.text import Tokenizer

    if not train_bios or not all(isinstance(text, str) for text in train_bios + validation_bios):
        raise ValueError("Provide nonempty training text and string biographies.")
    tokenizer = Tokenizer(num_words=MAX_WORDS, oov_token=OOV_TOKEN)
    tokenizer.fit_on_texts(train_bios)
    if reserve_missing:
        tokenizer.word_index = {
            word: index + int(index >= 2) for word, index in tokenizer.word_index.items()
        }
        tokenizer.word_index[MISSING_BIOGRAPHY_TOKEN] = 2
        tokenizer.index_word = {index: word for word, index in tokenizer.word_index.items()}
    train_sequences = tokenizer.texts_to_sequences(train_bios)
    length = min(512, max(1, max(map(len, train_sequences))))
    return (
        encode_bios(tokenizer, train_bios, length),
        encode_bios(tokenizer, validation_bios, length),
        tokenizer,
        length,
    )


def load_vectors(path: Path, word_index: dict, *, expected_sha256: str) -> tuple:
    """Load training-vocabulary vectors and verify the local file in one pass.

    Return a float32 20000 x 300 matrix and numeric coverage metadata.
    Missing words and unknown use the mean of matched training vectors;
    padding alone stays zero. Validation words never expand the mapping.
    """
    matrix = np.zeros((MAX_WORDS, WORD_WIDTH), dtype=np.float32)
    if word_index.get(OOV_TOKEN) != 1:
        raise ValueError("The tokenizer must reserve index one for unknown words.")
    remaining = {
        word.encode("utf-8"): index
        for word, index in word_index.items()
        if word not in {OOV_TOKEN, MISSING_BIOGRAPHY_TOKEN} and 1 < index < MAX_WORDS
    }
    if not remaining:
        raise ValueError("There are no training words to match against fastText.")
    requested = len(remaining)
    digest, matched = hashlib.sha256(), []
    with path.open("rb") as stream:
        header_line = next(stream, b"")
        digest.update(header_line)
        header = header_line.split()
        if len(header) != 2 or not header[0].isdigit() or header[1] != str(WORD_WIDTH).encode():
            raise ValueError("Expected a fastText .vec file with 300 values per word.")
        for line in stream:
            digest.update(line)
            word, _, values = line.partition(b" ")
            index = remaining.pop(word, None)
            if index is None:
                continue
            vector = np.asarray(values.split(), dtype=np.float32)
            if vector.shape != (WORD_WIDTH,) or not np.isfinite(vector).all():
                raise ValueError("A selected fastText vector is invalid.")
            matrix[index] = vector
            matched.append(index)
    if digest.hexdigest() != expected_sha256:
        raise ValueError("The fastText file does not match its frozen source manifest.")
    if not matched:
        raise ValueError("No training words were found in the fastText vectors.")
    fallback = matrix[matched].mean(axis=0)
    matrix[1] = fallback
    for index in remaining.values():
        matrix[index] = fallback
    return matrix, {
        "sha256": digest.hexdigest(),
        "requested_words": requested,
        "matched_words": len(matched),
        "missing_words": len(remaining),
        "matched_fraction": len(matched) / requested,
        "unknown_index": 1,
        "padding_index": 0,
        "missing_vector_policy": "Mean of matched training word vectors; padding remains zero.",
    }


def base_network(
    embedding_matrix: np.ndarray,
    max_len: int,
    mode: str,
    *,
    schema: dict,
    photos_enabled: bool = False,
    face_statistics: dict | None = None,
    robust_inputs: bool = False,
    modality_dropout: float = 0.0,
):
    """Join 39 structured, 20 projected face and 64 biography values.

    Freeze fastText. With photos enabled, Biased trains the face
    projection, BiLSTM and 40 -> 20 -> 1 head. Neutral freezes both media
    encoders and masks all research and both media branches inside the
    saved network as well as in the loader. Inputs are structured N x 39,
    token IDs N x max_len and frozen face features N x 2048.
    """
    from tensorflow import keras

    if mode not in {"neutral", "biased"}:
        raise ValueError("Select neutral or biased training.")
    validate_feature_schema(schema)
    if embedding_matrix.shape != (MAX_WORDS, WORD_WIDTH) or max_len < 1:
        raise ValueError("Expected a 20000 x 300 embedding matrix and a positive text length.")
    layers = keras.layers
    text_input = layers.Input(shape=(max_len,), dtype="int32", name="biography")
    embedding = layers.Embedding(
        MAX_WORDS,
        WORD_WIDTH,
        weights=[embedding_matrix],
        trainable=False,
        mask_zero=True,
        name="fasttext",
    )(text_input)
    text_embedding = layers.Bidirectional(
        layers.LSTM(
            32,
            activation="tanh",
            trainable=mode == "biased",
            use_cudnn="auto" if mode == "biased" else False,
        ),
        name="biography_encoder",
        trainable=mode == "biased",
    )(embedding)
    if robust_inputs:
        text_present = keras.ops.cast(keras.ops.not_equal(text_input[:, :1], 2), "float32")
        text_embedding = layers.Multiply(name="biography_presence")([text_embedding, text_present])
        text_embedding = layers.Dropout(
            modality_dropout, noise_shape=(None, 1), name="biography_modality_dropout"
        )(text_embedding)
    numeric_input = layers.Input(shape=(STRUCTURED_WIDTH,), name="structured")
    face_input = layers.Input(shape=(FACE_BACKBONE_WIDTH,), name="face_features")
    normalized_faces = face_input
    if photos_enabled:
        if not face_statistics or set(face_statistics) != {"mean", "variance"}:
            raise ValueError("Photo training needs frozen training-only normalization statistics.")
        normalized_faces = layers.Normalization(
            mean=face_statistics["mean"],
            variance=face_statistics["variance"],
            name="face_normalization",
            trainable=False,
        )(face_input)
    face_embedding = layers.Dense(
        FACE_WIDTH,
        use_bias=False,
        name="face_projection",
        trainable=photos_enabled and mode == "biased",
    )(normalized_faces)
    if robust_inputs:
        face_present = keras.ops.cast(
            keras.ops.any(keras.ops.not_equal(face_input, 0), axis=1, keepdims=True), "float32"
        )
        face_embedding = layers.Multiply(name="photo_presence")([face_embedding, face_present])
        face_embedding = layers.Dropout(
            modality_dropout, noise_shape=(None, 1), name="photo_modality_dropout"
        )(face_embedding)
    if not photos_enabled or mode == "neutral":
        face_embedding = layers.Rescaling(0.0, name="hide_face")(face_embedding)
    structured = numeric_input
    if mode == "neutral":
        structured = keras.ops.concatenate(
            [
                numeric_input[:, :PROFESSIONAL_WIDTH],
                keras.ops.zeros_like(numeric_input[:, PROFESSIONAL_WIDTH:]),
            ],
            axis=1,
        )
        text_embedding = layers.Rescaling(0.0, name="hide_biography")(text_embedding)
    combined = layers.Concatenate(name="combined_features")(
        [structured, face_embedding, text_embedding],
    )
    hidden = layers.Dense(40, activation="relu", name="embedding_layer")(combined)
    hidden = layers.Dropout(0.3, name="scoring_dropout")(hidden)
    hidden = layers.Dense(20, activation="relu", name="scoring_hidden")(hidden)
    output = layers.Dense(1, activation="sigmoid", name="scoring_output")(hidden)
    if robust_inputs:
        output = layers.Multiply(name="mandatory_gate")([output, numeric_input[:, 15:16]])
    return keras.Model(inputs=[numeric_input, text_input, face_input], outputs=output)


def evaluate_scores(labels, scores, cutoff: float = 0.5, *, mandatory=None) -> dict:
    """Measure binary decisions; rows are true classes in the confusion matrix.

    Balanced accuracy is undefined when either true class is absent.
    Macro-F1 always includes both classes, with zero for an empty term.
    """
    targets, probabilities = _score_arrays(labels, scores)
    if not np.isfinite(cutoff) or not 0 <= cutoff <= 1:
        raise ValueError("A decision cutoff must be finite and in [0, 1].")
    predicted = probabilities >= cutoff
    raw_failures = None
    if mandatory is not None:
        gate = np.asarray(mandatory).reshape(-1)
        if gate.shape != targets.shape or not np.isin(gate, (0, 1)).all():
            raise ValueError("Supply one binary hard-condition result per score.")
        raw_failures = int(np.sum((gate == 0) & predicted))
        predicted &= gate == 1
    tn = int(np.sum((targets == 0) & ~predicted))
    fp = int(np.sum((targets == 0) & predicted))
    fn = int(np.sum((targets == 1) & ~predicted))
    tp = int(np.sum((targets == 1) & predicted))
    recall_zero = tn / (tn + fp) if tn + fp else None
    recall_one = tp / (tp + fn) if tp + fn else None
    f1_zero = 2 * tn / (2 * tn + fp + fn) if 2 * tn + fp + fn else 0.0
    f1_one = 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else 0.0
    clipped = np.clip(probabilities, 1e-7, 1 - 1e-7)
    return {
        "samples": len(targets),
        "cutoff": float(cutoff),
        "binary_crossentropy": float(
            -np.mean(targets * np.log(clipped) + (1 - targets) * np.log1p(-clipped))
        ),
        "accuracy": (tp + tn) / len(targets),
        "balanced_accuracy": (
            (recall_zero + recall_one) / 2
            if recall_zero is not None and recall_one is not None
            else None
        ),
        "macro_f1": (f1_zero + f1_one) / 2,
        "confusion_matrix": [[tn, fp], [fn, tp]],
        "confusion_order": [0, 1],
        **(
            {
                "raw_mandatory_failure_acceptances": raw_failures,
                "mandatory_failed_rows": int(np.sum(gate == 0)),
                "raw_mandatory_failure_acceptance_rate": (
                    raw_failures / int(np.sum(gate == 0)) if np.any(gate == 0) else None
                ),
                "final_mandatory_failure_acceptances": 0,
            }
            if mandatory is not None
            else {}
        ),
    }


def select_cutoff(validation_labels, validation_scores, *, mandatory=None) -> dict:
    """Choose a validation cutoff by balanced accuracy, then macro-F1.

    Use the midpoint of the widest tied decision interval. Remaining
    ties prefer the midpoint nearest 0.5, then the smaller cutoff.
    Test scores must not be supplied to this development operation.
    """
    targets, scores = _score_arrays(validation_labels, validation_scores)
    if set(targets) != {0, 1}:
        raise ValueError("Cutoff selection needs both validation classes.")
    boundaries = np.unique(np.concatenate(([0.0], scores, [1.0])))
    intervals = [(0.0, 0.0)] + list(zip(boundaries[:-1], boundaries[1:]))
    choices = []
    for lower, upper in intervals:
        cutoff = float((lower + upper) / 2)
        metrics = evaluate_scores(targets, scores, cutoff, mandatory=mandatory)
        key = (
            metrics["balanced_accuracy"],
            metrics["macro_f1"],
            float(upper - lower),
            -abs(cutoff - 0.5),
            -cutoff,
        )
        choices.append((key, metrics, [float(lower), float(upper)]))
    _, metrics, interval = max(choices, key=lambda item: item[0])
    return {"source": "validation", "decision_interval": interval, **metrics}


def _score_arrays(labels, scores) -> tuple:
    targets = np.asarray(labels, dtype=np.float64).reshape(-1)
    probabilities = np.asarray(scores, dtype=np.float64).reshape(-1)
    if (
        not len(targets)
        or targets.shape != probabilities.shape
        or not np.isin(targets, (0, 1)).all()
        or not np.isfinite(probabilities).all()
        or np.any((probabilities < 0) | (probabilities > 1))
    ):
        raise ValueError("Provide matching binary labels and finite scores in [0, 1].")
    return targets, probabilities


def verify_roundtrip(
    model,
    path: Path,
    inputs: list[np.ndarray],
    mode: str,
    *,
    photos_enabled: bool = False,
) -> dict:
    """Check an untrained model's masks, weights and safe Keras round trip.

    Inputs contain a few development rows. No optimizer, gradients or
    classification metrics are created. Saved weights are diagnostic
    initial weights and must not be published as a trained model.
    """
    from tensorflow import keras

    if mode not in {"neutral", "biased"} or getattr(model, "optimizer", None) is not None:
        raise ValueError("Round-trip preflight requires an uncompiled model and a known mode.")
    if path.exists():
        raise FileExistsError("Choose a new preflight path; existing evidence is retained.")
    path.parent.mkdir(parents=True, exist_ok=True)
    before = _weight_digest(model)
    scores = (
        _check_full_model(model, inputs, mode)
        if photos_enabled
        else _check_masks(model, inputs, mode)
    )
    model.save(path)
    restored = keras.models.load_model(path, compile=False, safe_mode=True)
    restored_scores = (
        _check_full_model(restored, inputs, mode)
        if photos_enabled
        else _check_masks(restored, inputs, mode)
    )
    np.testing.assert_allclose(scores, restored_scores, atol=1e-7, rtol=1e-6)
    if _weight_digest(model) != before or _weight_digest(restored) != before:
        raise ValueError("The forward or serialization check changed model weights.")
    return {
        "status": "PREFLIGHT_NOT_TRAINED",
        "checked_rows": len(scores),
        "output_shape": list(scores.shape),
        "finite_outputs": True,
        "maximum_roundtrip_error": float(np.max(np.abs(scores - restored_scores))),
        "photo_input_invariant": not photos_enabled or mode == "neutral",
        "neutral_research_and_text_invariant": mode == "neutral",
        "trainable_parameters": sum(int(np.prod(w.shape)) for w in restored.trainable_weights),
        "photo_projection_trainable": photos_enabled and mode == "biased",
        "optimizer_created": False,
        "gradient_updates": 0,
        "weights_unchanged": True,
    }


def _weight_digest(model) -> str:
    digest = hashlib.sha256()
    for weights in model.get_weights():
        digest.update(str(weights.shape).encode())
        digest.update(weights.tobytes())
    return digest.hexdigest()


def _check_masks(model, inputs: list[np.ndarray], mode: str) -> np.ndarray:
    from tensorflow import keras

    if model.get_layer("fasttext").trainable or model.get_layer("face_projection").trainable:
        raise ValueError("The word embedding and no-photo projection must remain frozen.")
    encoder = model.get_layer("biography_encoder")
    if mode == "neutral" and any(
        layer.trainable_weights
        for layer in (encoder, encoder.forward_layer, encoder.backward_layer)
    ):
        raise ValueError("Neutral must freeze both internal LSTMs as well as their wrapper.")
    scores = np.asarray(model(inputs, training=False))
    if scores.shape != (len(inputs[0]), 1) or not np.isfinite(scores).all():
        raise ValueError("The model did not return one finite score per development row.")
    changed = [np.array(value, copy=True) for value in inputs]
    changed[2][:] = 1
    np.testing.assert_allclose(scores, model(changed, training=False), atol=1e-7, rtol=0)
    hidden_face = keras.Model(model.inputs, model.get_layer("hide_face").output)
    if np.any(np.asarray(hidden_face(changed, training=False))):
        raise ValueError("The saved no-photo branch must return twenty zeros.")
    if mode == "neutral":
        changed[0][:, PROFESSIONAL_WIDTH:] = 1
        changed[1][:] = 1
        np.testing.assert_allclose(scores, model(changed, training=False), atol=1e-7, rtol=0)
    return scores


def _check_full_model(model, inputs, mode):
    from tensorflow import keras

    if model.get_layer("fasttext").trainable:
        raise ValueError("The pretrained word vectors must remain frozen.")
    if model.get_layer("face_projection").trainable != (mode == "biased"):
        raise ValueError("The face projector trainability does not match its condition.")
    scores = np.asarray(model(inputs, training=False))
    if scores.shape != (len(inputs[0]), 1) or not np.isfinite(scores).all():
        raise ValueError("Full model did not return finite N x 1 scores.")
    if mode == "neutral":
        changed = [np.array(value, copy=True) for value in inputs]
        changed[0][:, PROFESSIONAL_WIDTH:] = 1
        changed[1][:] = 1
        changed[2][:] = 37
        np.testing.assert_allclose(scores, model(changed, training=False), atol=1e-7, rtol=0)
        encoder = model.get_layer("biography_encoder")
        if any(
            layer.trainable_weights
            for layer in (encoder, encoder.forward_layer, encoder.backward_layer)
        ):
            raise ValueError("Neutral's internal recurrent cells must remain frozen.")
    return scores


def train_hiring_tool(
    model,
    train: tuple,
    validation: tuple,
    *,
    learning_rate: float,
    batch_size: int,
    max_epochs: int,
    patience: int,
    output_directory: Path,
    photos_enabled: bool = False,
    train_weights=None,
    validation_weights=None,
):
    """Fit one label set; log epochs and checkpoint minimum validation loss."""
    from tensorflow import keras

    numeric_train, bios_train, faces_train, labels_train = train
    numeric_val, bios_val, faces_val, labels_val = validation
    if not photos_enabled and (
        np.any(faces_train) or np.any(faces_val) or model.get_layer("face_projection").trainable
    ):
        raise ValueError("This training operation is restricted to the frozen no-photo pilot.")
    output_directory.mkdir(parents=True, exist_ok=False)
    fitting_model = model
    checkpoint_name = "best.weights.h5"
    if photos_enabled and not model.get_layer("face_projection").trainable:
        fitting_model = _neutral_training_view(model)
        checkpoint_name = "best-head.weights.h5"
        sample = [array[:4] for array in train[:3]]
        np.testing.assert_allclose(
            model(sample, training=False), fitting_model(sample, training=False), atol=1e-7, rtol=0
        )
    fitting_model.compile(
        optimizer=keras.optimizers.Adam(
            learning_rate=learning_rate,
            weight_decay=0.0001 if photos_enabled else None,
            clipnorm=1.0 if photos_enabled else None,
        ),
        loss=keras.losses.BinaryCrossentropy(),
        metrics=[keras.metrics.BinaryAccuracy(name="accuracy")],
        jit_compile=False,
    )
    history = fitting_model.fit(
        [numeric_train, bios_train, faces_train],
        labels_train,
        sample_weight=train_weights,
        validation_data=(
            ([numeric_val, bios_val, faces_val], labels_val, validation_weights)
            if validation_weights is not None
            else ([numeric_val, bios_val, faces_val], labels_val)
        ),
        batch_size=batch_size,
        epochs=max_epochs,
        verbose=2,
        callbacks=[
            keras.callbacks.ReduceLROnPlateau(
                monitor="val_loss",
                factor=0.5,
                patience=3,
                min_lr=0.00001,
            ),
            keras.callbacks.CSVLogger(str(output_directory / "epochs.csv")),
            keras.callbacks.ModelCheckpoint(
                str(output_directory / checkpoint_name),
                monitor="val_loss",
                mode="min",
                save_best_only=True,
                save_weights_only=True,
            ),
            keras.callbacks.EarlyStopping(
                monitor="val_loss",
                patience=patience,
                restore_best_weights=True,
            ),
        ],
    )
    if fitting_model is not model:
        fitting_model.load_weights(output_directory / checkpoint_name)
        model.save_weights(output_directory / "best.weights.h5")
    return history


def _neutral_training_view(model):
    """Share scoring layers while skipping branches that are identically zero."""
    from tensorflow import keras

    numeric = keras.Input((39,), name="neutral_numeric")
    tokens = keras.Input(model.inputs[1].shape[1:], dtype="int32", name="neutral_tokens")
    faces = keras.Input((2048,), name="neutral_faces")
    hidden_research = keras.ops.zeros_like(numeric[:, 16:])
    text_zero = keras.ops.zeros_like(
        keras.ops.cast(keras.ops.sum(tokens, axis=1, keepdims=True), "float32")
    )
    face_zero = keras.ops.zeros_like(keras.ops.sum(faces, axis=1, keepdims=True))
    combined = keras.ops.concatenate(
        [
            numeric[:, :16],
            hidden_research,
            keras.ops.repeat(face_zero, 20, axis=1),
            keras.ops.repeat(text_zero, 64, axis=1),
        ],
        axis=1,
    )
    for name in ("embedding_layer", "scoring_dropout", "scoring_hidden", "scoring_output"):
        combined = model.get_layer(name)(combined)
    return keras.Model([numeric, tokens, faces], combined, name="neutral_training_view")


def _structured_scoring_view(model, mode):
    """Warm up the shared head without learning shortcuts from media."""
    from tensorflow import keras

    numeric = keras.Input((39,))
    values = (
        numeric
        if mode == "biased"
        else keras.ops.concatenate(
            [
                numeric[:, :16],
                keras.ops.zeros_like(numeric[:, 16:]),
            ],
            axis=1,
        )
    )
    zeros = keras.ops.repeat(keras.ops.zeros_like(numeric[:, :1]), 84, axis=1)
    values = keras.ops.concatenate([values, zeros], axis=1)
    for name in ("embedding_layer", "scoring_dropout", "scoring_hidden", "scoring_output"):
        values = model.get_layer(name)(values)
    return keras.Model(numeric, values)


def _reuse_frozen_vectors(root, word_index, expected_sha256):
    """Reuse checksum-verified pretrained rows, remapping only training words.

    The archived embedding was frozen throughout training. Its unknown
    rows are rebuilt from the current matched training vocabulary. No
    scoring, recurrent or face weights are transferred.
    """
    import io
    import json
    import zipfile
    import h5py

    candidates = sorted(
        (root / "runs/training-500-multimodal-v1").glob("*/run.json"), reverse=True
    )
    wanted = {
        word: index
        for word, index in word_index.items()
        if word not in {OOV_TOKEN, MISSING_BIOGRAPHY_TOKEN} and index < MAX_WORDS
    }
    for path in candidates:
        prior = json.loads(path.read_text(encoding="utf-8"))
        if (
            prior.get("status") != "TRAINED_RESEARCH_MODELS_TEST_SEALED"
            or prior["fasttext"]["sha256"] != expected_sha256
        ):
            continue
        old_index = json.loads(prior["tokenizer"]["config"]["word_index"])
        if any(word not in old_index or old_index[word] >= MAX_WORDS for word in wanted):
            continue
        seed = prior["conditions"]["neutral"]["seeds"][0]
        name = seed["directory"] + "/model.keras"
        checkpoint = path.parent / name
        with checkpoint.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != prior["provenance"]["artifact_sha256"][name]:
            continue
        matrices = []
        with zipfile.ZipFile(checkpoint) as archive:
            with h5py.File(io.BytesIO(archive.read("model.weights.h5")), "r") as weights:

                def collect(name, value):
                    if isinstance(value, h5py.Dataset) and value.shape == (MAX_WORDS, WORD_WIDTH):
                        matrices.append(value[...])

                weights.visititems(collect)
        if len(matrices) != 1:
            raise ValueError("The verified archive has an ambiguous word embedding.")
        old = matrices[0]
        matrix, matched, missing = np.zeros_like(old), [], []
        for word, index in wanted.items():
            vector = old[old_index[word]]
            if np.array_equal(vector, old[1]):
                missing.append(index)
            else:
                matrix[index] = vector
                matched.append(index)
        if not matched:
            continue
        fallback = matrix[matched].mean(axis=0)
        matrix[1] = fallback
        for index in missing:
            matrix[index] = fallback
        print("Reusing verified frozen fastText word vectors.", flush=True)
        return matrix, {
            "sha256": expected_sha256,
            "requested_words": len(wanted),
            "matched_words": len(matched),
            "missing_words": len(missing),
            "matched_fraction": len(matched) / len(wanted),
            "unknown_index": 1,
            "padding_index": 0,
            "missing_biography_index": 2,
            "cache_model_sha256": actual,
            "cache_source": str(checkpoint.relative_to(root)),
        }
    return None


def _paired_loss(targets, predictions):
    """Penalize wrong classes, failed hard checks and controlled decision pairs.

    Targets contain label, mandatory flag and controlled-pair flag.
    Consecutive rows form a pair. Equal labels require both predictions
    on the correct side; they need not have equal probabilities.
    """
    import tensorflow as tf

    labels, gate, controlled = targets[:, 0], targets[:, 1], targets[:, 2]
    probability = tf.clip_by_value(predictions[:, 0], 1e-7, 1 - 1e-7)
    binary = -(labels * tf.math.log(probability) + (1 - labels) * tf.math.log1p(-probability))
    margin = tf.nn.relu(0.15 - (2 * labels - 1) * (probability - 0.5))
    p, y = tf.reshape(probability, (-1, 2)), tf.reshape(labels, (-1, 2))
    different = tf.abs(y[:, 1] - y[:, 0])
    direction = y[:, 1] - y[:, 0]
    gap = different * tf.nn.relu(0.5 - direction * (p[:, 1] - p[:, 0]))
    pair = tf.repeat(gap, 2) + margin
    return binary + 2 * controlled * pair + 2 * (1 - gate) * tf.square(probability)


def _pair_dataset(inputs, targets, weights, metadata, batch_size, seed):
    """Shuffle complete contrast pairs, leaving validation people out of fitting."""
    import tensorflow as tf

    if batch_size < 2 or batch_size % 2:
        raise ValueError("Pair training needs an even batch size.")
    groups, ordinary, contrasts = [], [], {}
    for index, row in enumerate(metadata):
        if targets[index, 2] and row.get("contrast_pair_id"):
            contrasts.setdefault(row["contrast_pair_id"], []).append(index)
        else:
            ordinary.append(index)
    for indices in contrasts.values():
        if len(indices) != 2:
            raise ValueError("A training contrast lost one of its two rows.")
        groups.append(indices)
    groups.extend(
        [ordinary[i], ordinary[min(i + 1, len(ordinary) - 1)]] for i in range(0, len(ordinary), 2)
    )
    with tf.device("/CPU:0"):
        arrays = [tf.constant(value) for value in inputs]
        truth, importance = tf.constant(targets), tf.constant(weights)
        indices = tf.constant(np.asarray(groups, dtype="int32"))

    def gather(index):
        rows = tf.reshape(index, (-1,))
        values = tuple(tf.gather(value, rows) for value in arrays)
        return (
            values[0] if len(values) == 1 else values,
            tf.gather(truth, rows),
            tf.gather(importance, rows),
        )

    options = tf.data.Options()
    options.threading.private_threadpool_size = 2
    return (
        tf.data.Dataset.from_tensor_slices(indices)
        .shuffle(len(groups), seed=seed)
        .batch(batch_size // 2)
        .map(gather)
        .prefetch(1)
        .with_options(options)
    )


def _case_validation(scores_by_mode, labels, numeric, metadata, cutoff, mode):
    """Evaluate missing-media panels and controlled decisions at a fixed cutoff."""
    outcomes, penalties = {}, []
    for media_mode, scores in scores_by_mode.items():
        metrics = evaluate_scores(labels, scores, cutoff, mandatory=numeric[:, 15])
        rows = [
            {**row, "score": float(score), "final_decision": int(gate == 1 and score >= cutoff)}
            for row, score, gate in zip(metadata, scores, numeric[:, 15])
            if row.get("contrast_pair_id")
        ]
        contrasts = evaluate_contrasts(rows, label="Y_B" if mode == "biased" else "Y_N")
        per_panel = {}
        for panel in ("ordinary", "boundary"):
            indices = [
                i for i, row in enumerate(metadata) if row.get("panel", "ordinary") == panel
            ]
            if indices:
                per_panel[panel] = {
                    "metrics": evaluate_scores(
                        labels[indices], scores[indices], cutoff, mandatory=numeric[indices, 15]
                    ),
                    "contrasts": evaluate_contrasts(
                        [row for row in rows if row.get("panel", "ordinary") == panel],
                        label="Y_B" if mode == "biased" else "Y_N",
                    ),
                }
        for family in ("name_cue", "gender"):
            if family in contrasts:
                item = contrasts[family]
                penalties.append(
                    item["missed_flips"] / max(1, item["expected_flips"])
                    + item["false_flips"] / max(1, item["pairs"] - item["expected_flips"])
                )
        outcomes[media_mode] = {"metrics": metrics, "contrasts": contrasts, "panels": per_panel}
    worst = min(
        min(item["metrics"]["balanced_accuracy"], item["metrics"]["macro_f1"])
        for item in outcomes.values()
    )
    hard_rate = max(
        item["metrics"]["raw_mandatory_failure_acceptance_rate"] or 0 for item in outcomes.values()
    )
    quality = worst - 0.25 * (float(np.mean(penalties)) if penalties else 0) - hard_rate
    return {"cutoff": float(cutoff), "quality": quality, "media_modes": outcomes}


def run_revised_experiment(
    root: Path,
    directory: Path,
    *,
    seeds=(42, 43, 44, 45, 46),
    batch_size=64,
    warmup_epochs=60,
    max_epochs=50,
    patience=8,
) -> dict:
    """Train source-corrected, paired models with optional media and a hard gate.

    Keep the test in its archived cohort. Missing text uses reserved ID
    two and missing faces use zero vectors. Both learned media branches
    are masked for absent inputs. Export uncompiled standard Keras
    models so serving does not depend on the training loss function.
    """
    import csv
    import json
    from collections import Counter
    from datetime import datetime, timezone
    from uuid import uuid4

    import pyarrow.parquet as pq
    import tensorflow as tf
    from tensorflow import keras

    tf.config.experimental.enable_tensor_float_32_execution(False)
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    from simrecrut_ml.features.methods.objective_rule import RULE_VERSION

    if manifest["rule_version"] != RULE_VERSION:
        raise ValueError("Rebuild development evidence with the current rule version.")
    if manifest["status"] != "ACCEPTANCE_READY" or manifest["development_splits"] != {
        "train": 300,
        "validation": 100,
    }:
        raise ValueError("Complete the revised development acceptance checks before training.")
    for name, expected in manifest["acceptance"]["artifact_sha256"].items():
        with (directory / name).open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise ValueError("A revised input changed after data acceptance.")
    schema = json.loads(
        (root.parent / "contracts/feature-schemas/professional-research-v2.json").read_text()
    )
    source = load_dataset(
        directory / "training_cases.parquet", "biased", schema=schema, photos_enabled=True
    )
    tokens_train, tokens_val, tokenizer, length = process_bios(
        source["train"][1], source["validation"][1], reserve_missing=True
    )
    metadata = pq.read_table(
        directory / "training_cases.parquet",
        columns=[
            "base_profile_id",
            "split",
            "job_id",
            "weight_code",
            "variant",
            "contrast_pair_id",
            "contrast_family",
            "panel",
            "Y_N",
            "Y_B",
        ],
        filters=[("split", "in", ["train", "validation"])],
    ).to_pylist()
    meta = {
        split: [row for row in metadata if row["split"] == split]
        for split in ("train", "validation")
    }
    assets = json.loads((root / "datasets/raw/pretrained/manifest.json").read_text())
    asset = next(row for row in assets["assets"] if row["file"] == "fasttext/crawl-300d-2M.vec")
    print("Loading verified training-only fastText vocabulary.", flush=True)
    cached_vectors = _reuse_frozen_vectors(root, tokenizer.word_index, asset["sha256"])
    embedding, coverage = cached_vectors or load_vectors(
        root / "datasets/raw/pretrained" / asset["file"],
        tokenizer.word_index,
        expected_sha256=asset["sha256"],
    )
    face_rows = pq.read_table(
        directory / "face_features.parquet", filters=[("split", "=", "train")]
    ).to_pylist()
    face_matrix = np.asarray([row["features"] for row in face_rows], dtype="float32")
    statistics = {
        "mean": face_matrix.mean(axis=0),
        "variance": np.maximum(face_matrix.var(axis=0), 1e-4),
    }
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = root / "runs" / directory.name / (stamp + "-" + uuid4().hex[:6])
    output.mkdir(parents=True)
    report = {
        "status": "TRAINING_IN_PROGRESS",
        "cohort": directory.name,
        "feature_schema": schema,
        "label_protocol": manifest["label_protocol"],
        "input_sha256": manifest["acceptance"]["artifact_sha256"],
        "test_evaluated": False,
        "test_cases_loaded": False,
        "sealed_test": manifest["sealed_test"],
        "precision": "float32; TF32 disabled",
        "seeds": list(seeds),
        "representative_seed": 42,
        "sequence_length": length,
        "tokenizer": json.loads(tokenizer.to_json()),
        "fasttext": coverage,
        "training": {
            "batch_size": batch_size,
            "warmup_epochs": warmup_epochs,
            "max_epochs": max_epochs,
            "patience": patience,
            "modality_dropout": 0.5,
            "pair_weight": 2,
            "hard_penalty": 2,
        },
        "missing_media": {
            "biography_token": 2,
            "face_vector": "2048 zeros",
            "branch_outputs": "masked to zero",
        },
        "conditions": {},
    }

    def persist():
        pending = output / "run.pending.json"
        pending.write_text(json.dumps(report, indent=2), encoding="utf-8")
        pending.replace(output / "run.json")

    persist()
    print("TRAINING_RUN_DIRECTORY=" + str(output), flush=True)
    schedule = [
        ("neutral", seeds[:1]),
        ("biased", seeds[:1]),
        ("neutral", seeds[1:]),
        ("biased", seeds[1:]),
    ]
    for mode, active_seeds in schedule:
        if not active_seeds:
            continue
        condition = report["conditions"].setdefault(mode, {"seeds": []})
        train_numeric, _, train_faces, _ = source["train"]
        val_numeric, _, val_faces, _ = source["validation"]
        train_labels = np.asarray(
            [row["Y_N" if mode == "neutral" else "Y_B"] for row in meta["train"]], dtype="float32"
        )
        val_labels = np.asarray(
            [row["Y_N" if mode == "neutral" else "Y_B"] for row in meta["validation"]],
            dtype="float32",
        )
        chosen = np.arange(len(train_labels))
        if mode == "neutral":
            seen, keep = set(), []
            for i, row in enumerate(meta["train"]):
                key = row["base_profile_id"], tuple(train_numeric[i, :16])
                if key not in seen:
                    seen.add(key)
                    keep.append(i)
            chosen = np.asarray(keep)
        train_meta = [meta["train"][i] for i in chosen]
        counts = Counter(row["base_profile_id"] for row in train_meta)
        weights = np.asarray(
            [len(train_meta) / len(counts) / counts[row["base_profile_id"]] for row in train_meta],
            dtype="float32",
        )
        gates = train_numeric[chosen, 15]
        controlled = np.asarray(
            [mode == "biased" and row["contrast_pair_id"] is not None for row in train_meta],
            dtype="float32",
        )
        targets = np.column_stack([train_labels[chosen], gates, controlled]).astype("float32")
        weights *= np.where(gates == 0, 3.0, 1.0)
        weights *= np.where(controlled == 1, 2.0, 1.0)
        weights /= weights.mean()
        for seed in active_seeds:
            keras.backend.clear_session()
            keras.utils.set_random_seed(seed)
            model = base_network(
                embedding,
                length,
                mode,
                schema=schema,
                photos_enabled=True,
                face_statistics=statistics,
                robust_inputs=True,
                modality_dropout=0.5,
            )
            kernel, bias = model.get_layer("embedding_layer").get_weights()
            kernel[39:] = 0
            model.get_layer("embedding_layer").set_weights([kernel, bias])
            numeric_view = _structured_scoring_view(model, mode)
            raw_view = keras.Model(model.inputs, model.get_layer("scoring_output").output)
            feature_view = keras.Model(model.inputs, model.get_layer("combined_features").output)
            head_input = keras.Input((123,))
            hidden = head_input
            for name in ("embedding_layer", "scoring_dropout", "scoring_hidden", "scoring_output"):
                hidden = model.get_layer(name)(hidden)
            head = keras.Model(head_input, hidden)
            path = output / mode / f"seed-{seed}"
            path.mkdir(parents=True)
            initial_word = hashlib.sha256(
                model.get_layer("fasttext").get_weights()[0].tobytes()
            ).hexdigest()
            initial_media = {
                name: [value.copy() for value in model.get_layer(name).get_weights()]
                for name in ("face_projection", "biography_encoder")
            }

            def predict_panels(structured_only=False):
                if structured_only or mode == "neutral":
                    score = numeric_view.predict(val_numeric, batch_size=512, verbose=0).reshape(
                        -1
                    )
                    return {
                        name: score for name in ("full", "no_photo", "no_biography", "no_media")
                    }
                combined = feature_view.predict(
                    [val_numeric, tokens_val, val_faces], batch_size=128, verbose=0
                )
                combined = np.asarray(combined)
                panels = {
                    "full": combined,
                    "no_photo": combined.copy(),
                    "no_biography": combined.copy(),
                    "no_media": combined.copy(),
                }
                panels["no_photo"][:, 39:59] = 0
                panels["no_biography"][:, 59:] = 0
                panels["no_media"][:, 39:] = 0
                return {
                    name: head.predict(values, batch_size=512, verbose=0).reshape(-1)
                    for name, values in panels.items()
                }

            class CaseValidation(keras.callbacks.Callback):
                def __init__(self, structured_only):
                    super().__init__()
                    self.structured_only = structured_only

                def on_epoch_end(self, epoch, logs=None):
                    result = _case_validation(
                        predict_panels(self.structured_only),
                        val_labels,
                        val_numeric,
                        meta["validation"],
                        0.5,
                        mode,
                    )
                    logs["val_case_quality"] = result["quality"]
                    hard = max(
                        item["metrics"]["raw_mandatory_failure_acceptances"]
                        for item in result["media_modes"].values()
                    )
                    print(
                        "CASE_VALIDATION",
                        mode,
                        seed,
                        epoch + 1,
                        round(result["quality"], 6),
                        "raw_hard",
                        hard,
                        flush=True,
                    )

            def fit_stage(view, inputs, stage, epochs, rate):
                view.compile(
                    optimizer=keras.optimizers.Adam(
                        learning_rate=rate, weight_decay=0.0001, clipnorm=1
                    ),
                    loss=_paired_loss,
                    jit_compile=False,
                )
                dataset = _pair_dataset(inputs, targets, weights, train_meta, batch_size, seed)
                checkpoint = path / (stage + ".weights.h5")
                history = view.fit(
                    dataset,
                    epochs=epochs,
                    verbose=2,
                    callbacks=[
                        CaseValidation(stage == "warmup"),
                        keras.callbacks.CSVLogger(str(path / (stage + "-epochs.csv"))),
                        keras.callbacks.ReduceLROnPlateau(
                            monitor="loss", factor=0.5, patience=3, min_lr=1e-5
                        ),
                        keras.callbacks.ModelCheckpoint(
                            str(checkpoint),
                            monitor="val_case_quality",
                            mode="max",
                            save_best_only=True,
                            save_weights_only=True,
                        ),
                        keras.callbacks.EarlyStopping(
                            monitor="val_case_quality",
                            mode="max",
                            patience=patience,
                            min_delta=0.0001,
                            restore_best_weights=True,
                        ),
                    ],
                )
                view.load_weights(checkpoint)
                return {
                    "epochs": len(history.history["loss"]),
                    "best_quality": float(max(history.history["val_case_quality"])),
                }

            print("TRAINING", mode, seed, "structured warmup", flush=True)
            warmup = fit_stage(
                numeric_view,
                [train_numeric[chosen]],
                "warmup",
                warmup_epochs if mode == "biased" else 100,
                0.001,
            )
            tuning = None
            if mode == "biased":
                print("TRAINING", mode, seed, "multimodal tuning", flush=True)
                tuning = fit_stage(
                    raw_view,
                    [train_numeric[chosen], tokens_train[chosen], train_faces[chosen]],
                    "multimodal",
                    max_epochs,
                    0.0001,
                )
            scores = predict_panels()
            candidates = [0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65]
            evaluated = [
                _case_validation(scores, val_labels, val_numeric, meta["validation"], cut, mode)
                for cut in candidates
            ]
            selected = max(
                evaluated, key=lambda item: (item["quality"], -abs(item["cutoff"] - 0.5))
            )
            model.save_weights(path / "best.weights.h5")
            model.save(path / "model.keras")
            restored = keras.models.load_model(path / "model.keras", compile=False, safe_mode=True)
            checks = {}
            for media_mode in scores:
                ix = np.linspace(0, len(val_labels) - 1, 24, dtype=int)
                text = tokens_val[ix].copy()
                face = val_faces[ix].copy()
                if media_mode in {"no_biography", "no_media"}:
                    text[:] = 0
                    text[:, 0] = 2
                if media_mode in {"no_photo", "no_media"}:
                    face[:] = 0
                actual = np.asarray(
                    restored([val_numeric[ix], text, face], training=False)
                ).reshape(-1)
                expected = scores[media_mode][ix] * val_numeric[ix, 15]
                np.testing.assert_allclose(actual, expected, atol=2e-5, rtol=2e-5)
                checks[media_mode] = float(np.max(abs(actual - expected)))
            if (
                initial_word
                != hashlib.sha256(
                    model.get_layer("fasttext").get_weights()[0].tobytes()
                ).hexdigest()
            ):
                raise ValueError("Fine tuning changed the frozen fastText embedding.")
            changed_media = {
                name: any(
                    not np.array_equal(a, b)
                    for a, b in zip(values, model.get_layer(name).get_weights())
                )
                for name, values in initial_media.items()
            }
            if any(value != (mode == "biased") for value in changed_media.values()):
                raise ValueError("The revised media encoders did not follow their freeze policy.")
            with (path / "validation_predictions.csv").open(
                "w", encoding="utf-8", newline=""
            ) as stream:
                writer = csv.DictWriter(
                    stream,
                    fieldnames=list(meta["validation"][0])
                    + ["media_mode", "raw_score", "score", "decision"],
                )
                writer.writeheader()
                for media_mode, values in scores.items():
                    for row, score, gate in zip(meta["validation"], values, val_numeric[:, 15]):
                        writer.writerow(
                            {
                                **row,
                                "media_mode": media_mode,
                                "raw_score": float(score),
                                "score": float(score * gate),
                                "decision": int(gate == 1 and score >= selected["cutoff"]),
                            }
                        )
            result = {
                "seed": seed,
                "directory": path.relative_to(output).as_posix(),
                "warmup": warmup,
                "multimodal": tuning,
                "validation": selected,
                "roundtrip_errors": checks,
                "fasttext_unchanged": True,
                "updated_media_encoders": changed_media,
            }
            (path / "validation.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
            condition["seeds"].append(result)
            persist()
            print(
                "FINISHED",
                mode,
                seed,
                "cutoff",
                selected["cutoff"],
                "quality",
                selected["quality"],
                flush=True,
            )
    report["status"] = "TRAINED_RESEARCH_MODELS_TEST_SEALED"
    persist()
    return {"report": str(output / "run.json"), "status": report["status"]}


def run_multimodal_experiment(
    root: Path,
    *,
    seeds=(42, 43, 44, 45, 46),
    learning_rates=(0.0003, 0.0001, 0.001),
    batch_size: int = 32,
    max_epochs: int = 100,
    patience: int = 10,
) -> dict:
    """Fit both complete Keras conditions without fitting or evaluating test rows.

    Use validation for the declared bounded learning-rate comparison.
    Keep seed 42 as the representative model, and report every seed
    rather than selecting a lucky initialization for release.
    """
    import csv
    import json
    from collections import Counter
    from datetime import datetime, timezone
    from importlib.metadata import version
    from uuid import uuid4

    import pyarrow.parquet as pq
    from tensorflow import keras

    directory = root / "datasets/clean/training-500-multimodal-v1"
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("status") != "ACCEPTANCE_READY":
        raise ValueError("Complete the frozen table's data acceptance checks before fitting.")
    schema_path = root.parent / "contracts/feature-schemas/professional-research-v2.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    table_path = directory / "training_cases.parquet"
    digest = hashlib.sha256(table_path.read_bytes()).hexdigest()
    if digest != manifest["acceptance"]["artifact_sha256"][table_path.name]:
        raise ValueError("The frozen table changed after its acceptance check.")
    if Counter(manifest["profile_splits"].values()) != {
        "train": 300,
        "validation": 100,
        "test": 100,
    }:
        raise ValueError("The complete cohort must contain exactly 300/100/100 people.")
    source = load_dataset(table_path, "biased", schema=schema, photos_enabled=True)
    neutral_source = load_dataset(table_path, "neutral", schema=schema, photos_enabled=True)
    text_train, text_validation, tokenizer, length = process_bios(
        source["train"][1],
        source["validation"][1],
    )
    pretrained = root / "datasets/raw/pretrained"
    assets = json.loads((pretrained / "manifest.json").read_text(encoding="utf-8"))
    asset = next(item for item in assets["assets"] if item["file"] == "fasttext/crawl-300d-2M.vec")
    print("Loading verified fastText vectors for the training vocabulary.", flush=True)
    embedding, coverage = load_vectors(
        pretrained / asset["file"],
        tokenizer.word_index,
        expected_sha256=asset["sha256"],
    )
    train_faces = pq.read_table(
        directory / "face_features.parquet", filters=[("split", "=", "train")]
    ).to_pylist()
    face_matrix = np.asarray([row["features"] for row in train_faces], dtype=np.float32)
    statistics = {
        "mean": face_matrix.mean(axis=0),
        "variance": np.maximum(face_matrix.var(axis=0), 1e-4),
    }
    metadata = pq.read_table(
        table_path,
        columns=[
            "base_profile_id",
            "split",
            "features_39",
            "job_id",
            "weight_code",
            "variant",
            "contrast_pair_id",
            "contrast_family",
            "Y_N",
            "Y_B",
            "research_json",
            "photo_id",
        ],
        filters=[("split", "in", ["train", "validation"])],
    ).to_pylist()
    mode_metadata = {}
    for mode in ("neutral", "biased"):
        seen = set()
        filtered = []
        for row in metadata:
            if mode == "neutral":
                key = row["base_profile_id"], tuple(
                    np.asarray(row["features_39"][:16], dtype=np.float32)
                )
                if key in seen:
                    continue
                seen.add(key)
            filtered.append(row)
        mode_metadata[mode] = {
            split: [row for row in filtered if row["split"] == split]
            for split in ("train", "validation")
        }
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = root / "runs/training-500-multimodal-v1" / f"{stamp}-{uuid4().hex[:6]}"
    output.mkdir(parents=True, exist_ok=False)
    report = {
        "status": "TRAINING_IN_PROGRESS",
        "cohort": manifest["cohort"],
        "training_table_sha256": digest,
        "feature_schema": schema,
        "label_protocol": manifest["label_protocol"],
        "test_cases_loaded": False,
        "test_evaluated": False,
        "keras_backend": keras.backend.backend(),
        "dependencies": {
            "tensorflow": __import__("tensorflow").__version__,
            "keras": version("keras"),
            "numpy": version("numpy"),
        },
        "input_widths": {"structured": 39, "biography": 64, "face": 20, "combined": 123},
        "max_epochs": max_epochs,
        "patience": patience,
        "batch_size": batch_size,
        "dropout": 0.3,
        "weight_decay": 0.0001,
        "gradient_clipnorm": 1.0,
        "seeds": list(seeds),
        "representative_seed": 42,
        "sequence_length": length,
        "fasttext": coverage,
        "tokenizer": json.loads(tokenizer.to_json()),
        "conditions": {},
        "capabilities": {"photos_enabled": True, "all_five_research_families": True},
    }

    def persist():
        pending = output / "run.pending.json"
        pending.write_text(json.dumps(report, indent=2), encoding="utf-8")
        pending.replace(output / "run.json")

    persist()
    print("TRAINING_RUN_DIRECTORY=" + str(output), flush=True)

    def fit(mode, seed, rate, suffix):
        keras.backend.clear_session()
        keras.utils.set_random_seed(seed)
        data = neutral_source if mode == "neutral" else source
        inputs, weights = {}, {}
        for split in ("train", "validation"):
            numeric, _, faces, labels = data[split]
            tokens = (
                (text_train if split == "train" else text_validation)
                if mode == "biased"
                else (np.zeros((len(numeric), length), dtype=np.int32))
            )
            inputs[split] = (numeric, tokens, faces, labels)
            meta = mode_metadata[mode][split]
            if len(meta) != len(labels):
                raise ValueError("Metadata and training arrays changed their common row order.")
            counts = Counter(row["base_profile_id"] for row in meta)
            weights[split] = np.asarray(
                [len(meta) / len(counts) / counts[row["base_profile_id"]] for row in meta],
                dtype=np.float32,
            )
        path = output / mode / f"seed-{seed}-{suffix}"
        model = base_network(
            embedding, length, mode, schema=schema, photos_enabled=True, face_statistics=statistics
        )
        check = verify_roundtrip(
            model,
            path.parent / f"preflight-{seed}-{suffix}.keras",
            [array[:4] for array in inputs["train"][:3]],
            mode,
            photos_enabled=True,
        )
        initial_frozen = hashlib.sha256(
            model.get_layer("fasttext").get_weights()[0].tobytes()
        ).hexdigest()
        initial_projection = model.get_layer("face_projection").get_weights()[0].copy()
        initial_text = [
            array.copy() for array in model.get_layer("biography_encoder").get_weights()
        ]
        print(f"TRAINING {mode} seed={seed} learning_rate={rate}", flush=True)
        history = train_hiring_tool(
            model,
            inputs["train"],
            inputs["validation"],
            learning_rate=rate,
            batch_size=batch_size,
            max_epochs=max_epochs,
            patience=patience,
            output_directory=path,
            photos_enabled=True,
            train_weights=weights["train"],
            validation_weights=weights["validation"],
        )
        model.load_weights(path / "best.weights.h5")
        if (
            hashlib.sha256(model.get_layer("fasttext").get_weights()[0].tobytes()).hexdigest()
            != initial_frozen
        ):
            raise ValueError("Training changed the frozen word vectors.")
        projection_changed = not np.array_equal(
            initial_projection, model.get_layer("face_projection").get_weights()[0]
        )
        text_changed = any(
            not np.array_equal(before, after)
            for before, after in zip(
                initial_text,
                model.get_layer("biography_encoder").get_weights(),
            )
        )
        if (mode == "biased" and not (projection_changed and text_changed)) or (
            mode == "neutral" and (projection_changed or text_changed)
        ):
            raise ValueError("The media branches did not follow their intended freeze policy.")
        numeric, tokens, faces, labels = inputs["validation"]
        representation = keras.Model(
            model.inputs, [model.output, model.get_layer("combined_features").output]
        )
        scores, combined = representation.predict(
            [numeric, tokens, faces], batch_size=64, verbose=0
        )
        scores = np.asarray(scores).reshape(-1)
        cutoff = select_cutoff(labels, scores, mandatory=numeric[:, 15])
        threshold = cutoff["cutoff"]
        raw = evaluate_scores(labels, scores, threshold)
        reference = [row["Y_N"] for row in mode_metadata[mode]["validation"]]
        predictions = []
        for row, score in zip(mode_metadata[mode]["validation"], scores):
            gate = int(row["features_39"][15])
            predictions.append(
                {key: value for key, value in row.items() if key != "features_39"}
                | {
                    "score": float(score),
                    "mandatory_pass": gate,
                    "raw_decision": int(score >= threshold),
                    "final_decision": int(gate == 1 and score >= threshold),
                }
            )
        with (path / "validation_predictions.csv").open(
            "w", encoding="utf-8", newline=""
        ) as stream:
            writer = csv.DictWriter(stream, fieldnames=list(predictions[0]))
            writer.writeheader()
            writer.writerows(predictions)
        contrast = evaluate_contrasts(predictions, label="Y_N" if mode == "neutral" else "Y_B")
        head_input = keras.Input((123,))
        head_output = head_input
        for name in ("embedding_layer", "scoring_dropout", "scoring_hidden", "scoring_output"):
            head_output = model.get_layer(name)(head_output)
        score_head = keras.Model(head_input, head_output)
        no_photo, no_text = combined.copy(), combined.copy()
        no_photo[:, 39:59] = 0
        no_text[:, 59:] = 0
        photo_zero = score_head.predict(no_photo, batch_size=256, verbose=0).reshape(-1)
        text_zero = score_head.predict(no_text, batch_size=256, verbose=0).reshape(-1)
        model.save(path / "model.keras")
        restored = keras.models.load_model(path / "model.keras", compile=False, safe_mode=True)
        restored_scores = np.asarray(
            restored([numeric[:8], tokens[:8], faces[:8]], training=False)
        ).reshape(-1)
        np.testing.assert_allclose(scores[:8], restored_scores, atol=1e-6, rtol=1e-5)
        result = {
            "seed": seed,
            "learning_rate": rate,
            "directory": str(path.relative_to(output)),
            "epochs_completed": len(history.history["loss"]),
            "best_epoch": int(np.argmin(history.history["val_loss"])) + 1,
            "best_validation_loss": float(np.min(history.history["val_loss"])),
            "validation": cutoff,
            "validation_raw": raw,
            "validation_at_0_5": evaluate_scores(labels, scores, mandatory=numeric[:, 15]),
            "validation_against_neutral_rule": evaluate_scores(
                reference, scores, threshold, mandatory=numeric[:, 15]
            ),
            "contrasts": contrast,
            "preflight": check,
            "face_projector_updated": projection_changed,
            "biography_encoder_updated": text_changed,
            "frozen_fasttext_unchanged": True,
            "save_reload_max_error": float(np.max(abs(scores[:8] - restored_scores))),
            "diagnostic_ablations_not_retrained": {
                "zero_photo": evaluate_scores(
                    labels, photo_zero, threshold, mandatory=numeric[:, 15]
                ),
                "zero_text": evaluate_scores(
                    labels, text_zero, threshold, mandatory=numeric[:, 15]
                ),
                "mean_absolute_photo_score_change": float(np.mean(abs(scores - photo_zero))),
                "mean_absolute_text_score_change": float(np.mean(abs(scores - text_zero))),
            },
        }
        (path / "validation.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print("VALIDATION", mode, seed, json.dumps(cutoff), flush=True)
        return result

    try:
        for mode in ("neutral", "biased"):
            initial = fit(mode, seeds[0], learning_rates[0], "initial")
            candidates = [initial]
            report["conditions"][mode] = {"development_comparison": candidates, "seeds": []}
            persist()
            if (
                initial["validation"]["balanced_accuracy"] < 0.9
                or initial["validation"]["macro_f1"] < 0.9
            ):
                for rate in learning_rates[1:]:
                    candidates.append(fit(mode, seeds[0], rate, "rate-" + str(rate)))
                    persist()
            chosen = max(
                candidates,
                key=lambda row: (
                    row["validation"]["balanced_accuracy"],
                    row["validation"]["macro_f1"],
                    -row["best_validation_loss"],
                    row["learning_rate"] == learning_rates[0],
                ),
            )
            condition = report["conditions"][mode]
            condition["selected_learning_rate"] = chosen["learning_rate"]
            condition["seeds"].append(chosen)
            persist()
            for seed in seeds[1:]:
                condition["seeds"].append(fit(mode, seed, chosen["learning_rate"], "stability"))
                persist()
            condition["stability"] = {
                metric: {
                    "mean": float(
                        np.mean([row["validation"][metric] for row in condition["seeds"]])
                    ),
                    "std": float(
                        np.std([row["validation"][metric] for row in condition["seeds"]])
                    ),
                }
                for metric in ("balanced_accuracy", "macro_f1", "accuracy")
            }
            persist()
        report["status"] = "TRAINED_RESEARCH_MODELS_TEST_SEALED"
    except Exception as error:
        report["status"] = "FAILED"
        report["failure_type"] = type(error).__name__
        persist()
        raise
    persist()
    return {
        "report": str(output / "run.json"),
        "status": report["status"],
        "conditions": {key: row["stability"] for key, row in report["conditions"].items()},
    }


def evaluate_contrasts(rows: list[dict], *, label: str) -> dict:
    """Count correct, false and missed flips in complete validation pairs."""
    pairs = {}
    for row in rows:
        if row.get("contrast_pair_id"):
            pairs.setdefault(row["contrast_pair_id"], []).append(row)
    grouped = {}
    for pair in pairs.values():
        if len(pair) != 2:
            raise ValueError("A validation contrast must contain exactly two variants.")
        left, right = sorted(pair, key=lambda row: row["variant"])
        family = left["contrast_family"]
        result = grouped.setdefault(
            family,
            {
                "pairs": 0,
                "correct_flips": 0,
                "false_flips": 0,
                "missed_flips": 0,
                "expected_flips": 0,
                "wrong_direction_flips": 0,
                "score_changes": [],
            },
        )
        expected = left[label] != right[label]
        actual = left["final_decision"] != right["final_decision"]
        correct_direction = left[label] == left["final_decision"]
        result["pairs"] += 1
        result["expected_flips"] += int(expected)
        result["correct_flips"] += int(expected and actual and correct_direction)
        result["false_flips"] += int(actual and (not expected or not correct_direction))
        result["missed_flips"] += int(expected and (not actual or not correct_direction))
        result["wrong_direction_flips"] += int(expected and actual and not correct_direction)
        result["score_changes"].append(right["score"] - left["score"])
    for result in grouped.values():
        changes = result.pop("score_changes")
        result["mean_score_change"] = float(np.mean(changes))
        result["max_absolute_score_change"] = float(np.max(np.abs(changes)))
        predicted = result["correct_flips"] + result["false_flips"]
        result["flip_precision"] = result["correct_flips"] / predicted if predicted else None
        result["flip_recall"] = (
            result["correct_flips"] / result["expected_flips"]
            if result["expected_flips"]
            else None
        )
    return grouped


def audit_trained_models(root: Path, report_path: Path) -> dict:
    """Compare both conditions on common validation rows and the archived diagnostic.

    Keep the already selected cutoffs. Neutral training folds identical
    professional vectors, so its shared scoring view restores the full
    validation panel for comparisons. No final-test rows are read.
    """
    import csv
    import json

    import pyarrow.parquet as pq
    from tensorflow import keras
    from tensorflow.keras.preprocessing.text import tokenizer_from_json

    report = json.loads(report_path.read_text(encoding="utf-8"))
    if report["status"] != "TRAINED_RESEARCH_MODELS_TEST_SEALED":
        raise ValueError("Wait for all training runs before auditing their saved models.")
    directory = root / "datasets/clean/training-500-multimodal-v1"
    schema = report["feature_schema"]
    data = load_dataset(
        directory / "training_cases.parquet", "biased", schema=schema, photos_enabled=True
    )["validation"]
    numeric, biographies, faces, _ = data
    tokenizer = tokenizer_from_json(json.dumps(report["tokenizer"]))
    tokens = encode_bios(tokenizer, biographies, report["sequence_length"])
    metadata = pq.read_table(
        directory / "training_cases.parquet",
        columns=[
            "base_profile_id",
            "job_id",
            "weight_code",
            "variant",
            "contrast_pair_id",
            "contrast_family",
            "research_json",
            "Y_N",
            "Y_B",
        ],
        filters=[("split", "=", "validation")],
    ).to_pylist()
    roles = {
        row["job_id"]: row["category"]
        for row in pq.read_table(directory / "job_rules.parquet").to_pylist()
    }
    legacy_path = root / "datasets/clean/training-100-v1/training_cases.parquet"
    legacy = (
        pq.read_table(legacy_path, filters=[("split", "=", "validation")]).to_pylist()
        if legacy_path.exists()
        else []
    )
    training_people = set(
        pq.read_table(
            directory / "training_cases.parquet",
            columns=["base_profile_id"],
            filters=[("split", "=", "train")],
        )
        .column("base_profile_id")
        .to_pylist()
    )
    legacy_overlap = len(training_people & {row["base_profile_id"] for row in legacy})
    comparison = {
        "validation_people": len({row["base_profile_id"] for row in metadata}),
        "validation_rows": len(metadata),
        "test_evaluated": False,
        "conditions": {},
    }
    comparison["active_area_coverage"] = {
        area: {
            "rows": int(np.count_nonzero(numeric[:, 5 + index] > 0)),
            "people": len(
                {
                    row["base_profile_id"]
                    for row, values in zip(metadata, numeric)
                    if values[5 + index] > 0
                }
            ),
            "nonzero_match_people": len(
                {
                    row["base_profile_id"]
                    for row, values in zip(metadata, numeric)
                    if values[5 + index] > 0 and values[index] > 0
                }
            ),
        }
        for index, area in enumerate(
            ("skills", "experience", "education", "languages", "projects")
        )
    }
    comparison["limits"] = [
        "Validation checks agreement with synthetic research labels, not real hiring accuracy.",
        "Rows reuse people, jobs and variants; row count is not an independent sample size.",
        "Education and project coverage is limited; inspect active-area people counts.",
        "Gender and ethnicity contrasts may change portrait identity and do not isolate visual causality.",
        "Research threshold and fictional group effects remain uncalibrated.",
    ]
    archived = (
        root
        / "runs/training-100-v1/no-photo/training/20261117T011750Z-seed42-6af6a7/biased/validation_predictions.csv"
    )
    if archived.exists():
        with archived.open(encoding="utf-8", newline="") as stream:
            baseline = list(csv.DictReader(stream))
        old_cutoffs = {float(row["validation_cutoff"]) for row in baseline}
        if len(old_cutoffs) != 1:
            raise ValueError("The archived run must have one saved validation cutoff.")
        old_cutoff = old_cutoffs.pop()
        old_gate = np.asarray([int(row["mandatory_pass"]) for row in baseline])
        old_labels = np.asarray([int(row["Y_B"]) for row in baseline])
        old_scores = np.asarray([float(row["score"]) for row in baseline])
        old_pairs = [
            {
                **row,
                "Y_B": int(row["Y_B"]),
                "score": float(row["score"]),
                "contrast_pair_id": row["base_profile_id"] + ":" + row["job_id"],
                "contrast_family": "referral",
                "final_decision": int(float(row["score"]) >= old_cutoff),
            }
            for row in baseline
        ]
        comparison["archived_biased_reference"] = {
            "source": str(archived.relative_to(root)),
            "raw_metrics": evaluate_scores(old_labels, old_scores, old_cutoff),
            "with_final_gate": evaluate_scores(
                old_labels, old_scores, old_cutoff, mandatory=old_gate
            ),
            "raw_contrasts": evaluate_contrasts(old_pairs, label="Y_B"),
            "comparison_limit": "Archived validation is a previously inspected diagnostic; new predictions zero the photo representation.",
        }
    for mode, condition in report["conditions"].items():
        outcomes = []
        for result in condition["seeds"]:
            path = report_path.parent / result["directory"]
            model = None
            threshold = result["validation"]["cutoff"]
            if mode == "neutral" or result["seed"] == 42:
                keras.backend.clear_session()
                model = keras.models.load_model(
                    path / "model.keras", compile=False, safe_mode=True
                )
            if mode == "neutral":
                view = _neutral_training_view(model)
                scores = view.predict([numeric, tokens, faces], batch_size=256, verbose=0).reshape(
                    -1
                )
                check_rows = np.linspace(0, len(scores) - 1, 12, dtype=int)
                actual = np.asarray(
                    model(
                        [numeric[check_rows], tokens[check_rows], faces[check_rows]],
                        training=False,
                    )
                ).reshape(-1)
                np.testing.assert_allclose(actual, scores[check_rows], atol=1e-6, rtol=1e-5)
            else:
                with (path / "validation_predictions.csv").open(
                    encoding="utf-8", newline=""
                ) as stream:
                    saved = list(csv.DictReader(stream))
                if len(saved) != len(metadata) or any(
                    (row["base_profile_id"], row["job_id"], row["weight_code"], row["variant"])
                    != (
                        source["base_profile_id"],
                        source["job_id"],
                        source["weight_code"],
                        source["variant"],
                    )
                    for row, source in zip(saved, metadata)
                ):
                    raise ValueError(
                        "Saved validation predictions do not match the frozen row order."
                    )
                scores = np.asarray([float(row["score"]) for row in saved])
            label = "Y_N" if mode == "neutral" else "Y_B"
            labels = np.asarray([row[label] for row in metadata])
            rows = [
                {
                    **row,
                    "score": float(score),
                    "mandatory_pass": int(values[15]),
                    "raw_decision": int(score >= threshold),
                    "final_decision": int(values[15] == 1 and score >= threshold),
                }
                for row, score, values in zip(metadata, scores, numeric)
            ]
            metrics = evaluate_scores(labels, scores, threshold, mandatory=numeric[:, 15])
            shared = evaluate_scores(
                [row["Y_N"] for row in metadata], scores, threshold, mandatory=numeric[:, 15]
            )
            groups = {}
            for family in ("role", *RESEARCH_FAMILIES):
                categories = sorted(
                    {
                        (
                            roles[row["job_id"]]
                            if family == "role"
                            else json.loads(row["research_json"])[family]
                        )
                        for row in metadata
                    }
                )
                for category in categories:
                    selected = [
                        index
                        for index, row in enumerate(metadata)
                        if (
                            roles[row["job_id"]]
                            if family == "role"
                            else json.loads(row["research_json"])[family]
                        )
                        == category
                    ]
                    values = numeric[selected]
                    groups[f"{family}:{category}"] = {
                        "people": len({metadata[index]["base_profile_id"] for index in selected}),
                        "acceptance_rate": float(
                            np.mean([rows[index]["final_decision"] for index in selected])
                        ),
                        "objective_acceptance_rate": float(
                            np.mean([metadata[index]["Y_N"] for index in selected])
                        ),
                        "shared_neutral_label": evaluate_scores(
                            [metadata[index]["Y_N"] for index in selected],
                            scores[selected],
                            threshold,
                            mandatory=values[:, 15],
                        ),
                        **evaluate_scores(
                            labels[selected], scores[selected], threshold, mandatory=values[:, 15]
                        ),
                    }
            common = {
                "seed": result["seed"],
                "own_label": metrics,
                "raw_decisions": evaluate_scores(labels, scores, threshold),
                "shared_neutral_label": shared,
                "contrasts": evaluate_contrasts(rows, label=label),
                "raw_contrasts": evaluate_contrasts(
                    [{**row, "final_decision": row["raw_decision"]} for row in rows],
                    label=label,
                ),
                "group_metrics": groups,
                "cutoff_reselected": False,
            }
            if mode == "neutral":
                with (path / "validation_common_predictions.csv").open(
                    "w", encoding="utf-8", newline=""
                ) as stream:
                    writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                    writer.writeheader()
                    writer.writerows(rows)
                common["common_view_checked_against_saved_model"] = True
            if result["seed"] == 42 and legacy:
                old_numeric = np.asarray([row["features_39"] for row in legacy], dtype=np.float32)
                old_tokens = encode_bios(
                    tokenizer, [row["biography"] for row in legacy], report["sequence_length"]
                )
                if np.any(np.all(old_tokens == 0, axis=1)):
                    raise ValueError(
                        "An archived biography is empty; do not invent replacement text."
                    )
                mean = np.asarray(model.get_layer("face_normalization").mean).reshape(-1)
                old_faces = np.tile(mean, (len(legacy), 1))
                old_scores = np.asarray(
                    model.predict([old_numeric, old_tokens, old_faces], batch_size=64, verbose=0)
                ).reshape(-1)
                old_rows = [
                    {
                        "base_profile_id": row["base_profile_id"],
                        "job_id": row["job_id"],
                        "contrast_pair_id": row["base_profile_id"] + ":" + row["job_id"],
                        "contrast_family": "referral",
                        "variant": row["variant"],
                        "Y_N": row["Y_N"],
                        "Y_B": row["Y_B"],
                        "score": float(score),
                        "mandatory_pass": int(values[15]),
                        "raw_decision": int(score >= threshold),
                        "final_decision": int(values[15] == 1 and score >= threshold),
                    }
                    for row, score, values in zip(legacy, old_scores, old_numeric)
                ]
                common["legacy_no_photo_diagnostic"] = {
                    "rows": len(legacy),
                    "previously_inspected": True,
                    "people_overlapping_current_training": legacy_overlap,
                    "independent_test": False,
                    "portrait_embedding_zeroed_with_training_mean": True,
                    "metrics": evaluate_scores(
                        [row[label] for row in legacy],
                        old_scores,
                        threshold,
                        mandatory=old_numeric[:, 15],
                    ),
                    "contrasts": evaluate_contrasts(old_rows, label=label),
                    "raw_decisions": evaluate_scores(
                        [row[label] for row in legacy], old_scores, threshold
                    ),
                    "raw_contrasts": evaluate_contrasts(
                        [{**row, "final_decision": row["raw_decision"]} for row in old_rows],
                        label=label,
                    ),
                }
                with (path / "legacy_validation_predictions.csv").open(
                    "w", encoding="utf-8", newline=""
                ) as stream:
                    writer = csv.DictWriter(stream, fieldnames=list(old_rows[0]))
                    writer.writeheader()
                    writer.writerows(old_rows)
            result["common_validation"] = common
            (path / "validation_common.json").write_text(
                json.dumps(common, indent=2), encoding="utf-8"
            )
            outcomes.append(common)
        comparison["conditions"][mode] = {
            key: {
                "mean": float(np.mean([item["own_label"][key] for item in outcomes])),
                "std": float(np.std([item["own_label"][key] for item in outcomes])),
            }
            for key in ("accuracy", "balanced_accuracy", "macro_f1")
        }
    report["common_validation"] = comparison
    report["preprocessing"] = {
        "face_backbone": "Frozen VGGFace2 ResNet50; cached 2048-value features.",
        "face_backbone_sha256": FACE_WEIGHT_SHA256,
        "face_rgb_crop": [224, 224, 3],
        "bgr_means": [91.4953, 103.8827, 131.0912],
        "normalization": "Training portraits only; parameters stored inside each Keras model.",
        "text": "Training vocabulary; unknown index 1, masked padding index 0, right padding.",
        "neutral_optimization": "Shared scoring layers skip frozen zero branches; the complete model is saved.",
        "ablation_scope": "Zeroed branch representations are perturbation diagnostics without retraining; they do not prove a branch improves performance.",
    }
    pending = report_path.with_suffix(".pending.json")
    pending.write_text(json.dumps(report, indent=2), encoding="utf-8")
    pending.replace(report_path)
    return comparison


def evaluate_revised_experiment(root: Path, report_path: Path) -> dict:
    """Audit saved development predictions without opening the sealed test.

    Require all four media modes to reach 0.97 balanced accuracy and
    macro-F1. All five bias families need 0.90 flip precision/recall;
    name and gender also need 0.80 on the ordinary panel. These are
    research acceptance checks, not evidence of real hiring accuracy.
    """
    import csv
    import json
    import pyarrow.parquet as pq

    report = json.loads(report_path.read_text(encoding="utf-8"))
    if report["status"] != "TRAINED_RESEARCH_MODELS_TEST_SEALED":
        raise ValueError("Finish every requested seed before accepting the experiment.")
    if set(report["conditions"]) != {"neutral", "biased"}:
        raise ValueError("Finish both model conditions before accepting the experiment.")
    if report["representative_seed"] not in report["seeds"]:
        raise ValueError("The preselected representative seed was not trained.")
    directory = root / "datasets/clean" / report["cohort"]
    for name, expected in report["input_sha256"].items():
        with (directory / name).open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise ValueError("A development input changed after training began.")
    rows = pq.read_table(
        directory / "training_cases.parquet",
        columns=[
            "base_profile_id",
            "features_39",
            "job_id",
            "weight_code",
            "variant",
            "contrast_pair_id",
            "contrast_family",
            "panel",
            "Y_N",
            "Y_B",
        ],
        filters=[("split", "=", "validation")],
    ).to_pylist()
    numeric = np.asarray([row["features_39"] for row in rows], dtype="float32")
    summary, failures, hashes = {}, [], {}
    for mode, condition in report["conditions"].items():
        if {item["seed"] for item in condition["seeds"]} != set(report["seeds"]):
            raise ValueError("A requested stability seed is missing.")
        collected = []
        for result in condition["seeds"]:
            path = report_path.parent / result["directory"]
            with (path / "validation_predictions.csv").open(
                encoding="utf-8", newline=""
            ) as stream:
                saved = list(csv.DictReader(stream))
            scores = {}
            for media_mode in ("full", "no_photo", "no_biography", "no_media"):
                selected = [row for row in saved if row["media_mode"] == media_mode]
                if len(selected) != len(rows) or any(
                    tuple(
                        item[key]
                        for key in ("base_profile_id", "job_id", "weight_code", "variant")
                    )
                    != tuple(
                        source[key]
                        for key in ("base_profile_id", "job_id", "weight_code", "variant")
                    )
                    for item, source in zip(selected, rows)
                ):
                    raise ValueError("A saved prediction changed its frozen validation row order.")
                values = np.asarray([float(row["raw_score"]) for row in selected])
                public = np.asarray([float(row["score"]) for row in selected])
                np.testing.assert_allclose(public, values * numeric[:, 15], atol=1e-7)
                expected_decisions = (numeric[:, 15] == 1) & (
                    values >= result["validation"]["cutoff"]
                )
                if not np.array_equal(
                    np.asarray([int(row["decision"]) for row in selected]), expected_decisions
                ):
                    raise ValueError("A saved decision disagrees with its recorded cutoff.")
                scores[media_mode] = values
            labels = np.asarray([row["Y_N" if mode == "neutral" else "Y_B"] for row in rows])
            check = _case_validation(
                scores, labels, numeric, rows, result["validation"]["cutoff"], mode
            )
            result["validated_predictions"] = True
            result["shared_neutral_validation"] = {
                media_mode: evaluate_scores(
                    [row["Y_N"] for row in rows],
                    values,
                    result["validation"]["cutoff"],
                    mandatory=numeric[:, 15],
                )
                for media_mode, values in scores.items()
            }
            for media_mode, outcome in check["media_modes"].items():
                metrics = outcome["metrics"]
                if metrics["balanced_accuracy"] < 0.97 or metrics["macro_f1"] < 0.97:
                    failures.append(
                        {
                            "mode": mode,
                            "seed": result["seed"],
                            "media_mode": media_mode,
                            "check": "global_metrics",
                        }
                    )
                if metrics["final_mandatory_failure_acceptances"]:
                    raise ValueError("The final decision bypassed a mandatory failure.")
                if metrics["raw_mandatory_failure_acceptances"]:
                    failures.append(
                        {
                            "mode": mode,
                            "seed": result["seed"],
                            "media_mode": media_mode,
                            "check": "raw_mandatory_errors",
                        }
                    )
                if mode == "biased":
                    for panel, limits in [(outcome, 0.9), (outcome["panels"]["ordinary"], 0.8)]:
                        families = RESEARCH_FAMILIES if limits == 0.9 else ("name_cue", "gender")
                        for family in families:
                            values = panel["contrasts"][family]
                            if (values["flip_precision"] or 0) < limits or (
                                values["flip_recall"] or 0
                            ) < limits:
                                failures.append(
                                    {
                                        "mode": mode,
                                        "seed": result["seed"],
                                        "media_mode": media_mode,
                                        "check": (
                                            "ordinary_contrasts"
                                            if limits == 0.8
                                            else "combined_contrasts"
                                        ),
                                        "family": family,
                                    }
                                )
                elif any(item["false_flips"] for item in outcome["contrasts"].values()):
                    failures.append(
                        {
                            "mode": mode,
                            "seed": result["seed"],
                            "media_mode": media_mode,
                            "check": "neutral_changed_with_research_inputs",
                        }
                    )
            for name in (
                "model.keras",
                "best.weights.h5",
                "validation.json",
                "validation_predictions.csv",
            ):
                with (path / name).open("rb") as stream:
                    hashes[(path / name).relative_to(report_path.parent).as_posix()] = (
                        hashlib.file_digest(stream, "sha256").hexdigest()
                    )
            collected.append(check)
        summary[mode] = {
            media_mode: {
                metric: {
                    "mean": float(
                        np.mean(
                            [
                                item["media_modes"][media_mode]["metrics"][metric]
                                for item in collected
                            ]
                        )
                    ),
                    "std": float(
                        np.std(
                            [
                                item["media_modes"][media_mode]["metrics"][metric]
                                for item in collected
                            ]
                        )
                    ),
                }
                for metric in ("accuracy", "balanced_accuracy", "macro_f1")
            }
            for media_mode in ("full", "no_photo", "no_biography", "no_media")
        }
    report["validation_summary"] = summary
    report["validation_support"] = {
        area: {
            "active_rows": int(np.sum(numeric[:, 5 + index] > 0)),
            "active_people": len(
                {
                    row["base_profile_id"]
                    for row, values in zip(rows, numeric)
                    if values[5 + index] > 0
                }
            ),
            "people_with_positive_match": len(
                {
                    row["base_profile_id"]
                    for row, values in zip(rows, numeric)
                    if values[5 + index] > 0 and values[index] > 0
                }
            ),
        }
        for index, area in enumerate(
            ("skills", "experience", "education", "languages", "projects")
        )
    }
    report["acceptance"] = {
        "passed": not failures,
        "representative_seed": report["representative_seed"],
        "representative_models_passed": not any(
            item["seed"] == report["representative_seed"] for item in failures
        ),
        "all_seed_checks_passed": not failures,
        "failures": failures,
        "validation_people": len({row["base_profile_id"] for row in rows}),
        "validation_rows": len(rows),
        "test_evaluated": False,
        "scope": "Uncalibrated controlled recruitment simulation.",
    }
    cpu_path = report_path.parent / "provenance/saved-model-cpu-check.json"
    if cpu_path.exists():
        cpu_check = json.loads(cpu_path.read_text(encoding="utf-8"))
        if cpu_check["test_evaluated"] or cpu_check["device"] != "CPU":
            raise ValueError("The CPU check must use development inputs only.")
        if set(cpu_check["models"]) != {"neutral", "biased"}:
            raise ValueError("Check both representative models on CPU.")
        for mode, item in cpu_check["models"].items():
            if item["seed"] != report["representative_seed"]:
                raise ValueError("The CPU check changed the preselected representative seed.")
            name = f"{mode}/seed-{item['seed']}/model.keras"
            if hashes[name] != item["sha256"]:
                raise ValueError("The CPU check refers to different saved weights.")
            if set(item["media_modes"]) != {"full", "no_photo", "no_biography", "no_media"}:
                raise ValueError("The CPU check omitted a missing-media mode.")
            if any(
                check["rows"] != len(rows)
                or check["decision_mismatches"]
                or check["mandatory_failure_acceptances"]
                for check in item["media_modes"].values()
            ):
                raise ValueError("The saved model failed a complete CPU validation check.")
        report["cpu_saved_model_check"] = cpu_check
        with cpu_path.open("rb") as stream:
            hashes[cpu_path.relative_to(report_path.parent).as_posix()] = hashlib.file_digest(
                stream, "sha256"
            ).hexdigest()
    report["artifact_sha256"] = hashes
    report["preprocessing"] = {
        "structured_inputs": "39 float32 values in the saved schema order.",
        "biography": "Saved training tokenizer; right padding and truncation to sequence_length.",
        "padding_index": 0,
        "unknown_index": 1,
        "missing_biography_index": 2,
        "face_backbone": "Frozen VGGFace2 ResNet50, keras-vggface release v2.0, no top.",
        "face_backbone_file": "datasets/raw/pretrained/vggface2/rcmalli_vggface_tf_notop_resnet50.h5",
        "face_backbone_sha256": FACE_WEIGHT_SHA256,
        "face_crop": "Reviewed RGB face crop, 224 x 224, pixels in [0, 255].",
        "face_preprocessing": "Convert RGB to BGR and subtract BGR channel means.",
        "bgr_means": [91.4953, 103.8827, 131.0912],
        "face_values": "2048 float32 backbone outputs; training-only normalization is inside each model.",
        "missing_face": "2048 zeros; the saved model masks the branch.",
        "scores": "Controlled simulation outputs, not calibrated hiring probabilities.",
    }
    pending = report_path.with_suffix(".pending.json")
    pending.write_text(json.dumps(report, indent=2), encoding="utf-8")
    pending.replace(report_path)
    return report["acceptance"]
