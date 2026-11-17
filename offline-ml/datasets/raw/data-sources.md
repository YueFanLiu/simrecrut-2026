# Raw data sources

## Djinni (English)

[Jobs](https://huggingface.co/datasets/lang-uk/recruitment-dataset-job-descriptions-english)
and [candidate profiles](https://huggingface.co/datasets/lang-uk/recruitment-dataset-candidate-profiles-english)
are downloaded as Parquet tables: 141,897 jobs and 210,250 profiles.
They contain text and fields such as job title, experience and English level.
Local folders: `djinni-jobs/data/` and `djinni-profiles/data/`.

## Resume Dataset

[Kaggle source](https://www.kaggle.com/datasets/snehaanbhawal/resume-dataset).
The ZIP contains 2,484 PDF resumes and a CSV with their text and job categories.
The publisher collected resume examples from LiveCareer.
Local folder: `resume-dataset/`.

## PopResume

[Source](https://huggingface.co/datasets/sumin-yu/PopResume), CC BY 4.0.
The local sample contains 20 synthetic software-developer resumes with photos
and matching text. One image shard and the occupation's text table are retained
as source backups; this is not a full dataset download.
Local folder: `popresume/`; its manifest records the revision and checksums.
The samples have no skills section or explicit language levels. One portrait
is blurred. The review is under `datasets/clean/popresume-review/`.

## Synthetic portraits

[Persona FAS Bench](https://huggingface.co/datasets/withpersona/persona-fas-bench-v1),
CC BY 4.0, redistributed from `saatvikbilla1/persona-fas`.
We use the pure synthetic `full_face_synthesis/imagen4` images and their generation
settings. The 360 reviewed portraits are study images, not photos of Djinni candidates.
Local folder: `persona-fas/`; the pinned revision is stored with each selected image.

## Pretrained model assets

[fastText English vectors](https://fasttext.cc/docs/en/english-vectors.html) and
[VGGFace2 ResNet-50 weights](https://github.com/rcmalli/keras-vggface/releases/tag/v2.0).
Local folder: `pretrained/`; its manifest records download links and checksums.

Keep dataset files, portraits and weights local and out of Git.
