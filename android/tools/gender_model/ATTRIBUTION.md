# Attribution to add IF you bundle a FairFace-derived gender model

FairFace is licensed **CC BY 4.0**, which requires attribution. If `app/src/main/assets/gender.tflite`
was trained on FairFace, add this to the app's Third-Party Notices (`../../../THIRD_PARTY_NOTICES.md`)
and, ideally, the store listing:

> The optional on-device gender estimate uses a model trained on the **FairFace** dataset
> (K. Kärkkäinen and J. Joo, "FairFace: Face Attribute Dataset for Balanced Race, Gender, and Age",
> https://github.com/joojs/fairface), used under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).

If you used a different source, replace this with that source's required attribution/license text.
Do **not** ship a model trained on UTKFace, IMDB-Wiki, or Adience — those are non-commercial only.
