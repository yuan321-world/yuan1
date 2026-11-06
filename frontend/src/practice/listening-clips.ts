import {catalogRecordings} from './listening-catalog-cues'
// Timing source and eight waveform checks: docs/content/2026-10-23-listening-clips.md.
// Match the content hash, never a database ID, exam label, or approximate duration.
const recordingSha256='3138dd4a84c4d9cf6de184efd41f7a5bd4a91d1aec518ccaf5944bdecda8e649'
export interface ListeningClipCue {key:string;questionKeys:string[];start:number;end:number}
const cues:ListeningClipCue[]=[
  {
    "key": "news1",
    "questionKeys": [
      "q1",
      "q2"
    ],
    "start": 40.451,
    "end": 143.486
  },
  {
    "key": "news2",
    "questionKeys": [
      "q3",
      "q4"
    ],
    "start": 160.32,
    "end": 279.489
  },
  {
    "key": "news3",
    "questionKeys": [
      "q5",
      "q6",
      "q7"
    ],
    "start": 296.757,
    "end": 414.86
  },
  {
    "key": "conversation1",
    "questionKeys": [
      "q8",
      "q9",
      "q10",
      "q11"
    ],
    "start": 464.727,
    "end": 631.265
  },
  {
    "key": "conversation2",
    "questionKeys": [
      "q12",
      "q13",
      "q14",
      "q15"
    ],
    "start": 648.165,
    "end": 808.502
  },
  {
    "key": "passage1",
    "questionKeys": [
      "q16",
      "q17",
      "q18"
    ],
    "start": 857.303,
    "end": 1003.939
  },
  {
    "key": "passage2",
    "questionKeys": [
      "q19",
      "q20",
      "q21"
    ],
    "start": 1020.339,
    "end": 1178.143
  },
  {
    "key": "passage3",
    "questionKeys": [
      "q22",
      "q23",
      "q24",
      "q25"
    ],
    "start": 1194.677,
    "end": 1391.648
  }
]
// March 2023: separate MP3, eight waveform matches, +61.49s source offset.
const march2023Cues:ListeningClipCue[]=[
  {
    "key": "news1",
    "questionKeys": [
      "q1",
      "q2"
    ],
    "start": 101.74,
    "end": 202.773
  },
  {
    "key": "news2",
    "questionKeys": [
      "q3",
      "q4"
    ],
    "start": 219.606,
    "end": 330.84
  },
  {
    "key": "news3",
    "questionKeys": [
      "q5",
      "q6",
      "q7"
    ],
    "start": 347.606,
    "end": 484.84
  },
  {
    "key": "conversation1",
    "questionKeys": [
      "q8",
      "q9",
      "q10",
      "q11"
    ],
    "start": 529.74,
    "end": 684.34
  },
  {
    "key": "conversation2",
    "questionKeys": [
      "q12",
      "q13",
      "q14",
      "q15"
    ],
    "start": 701.14,
    "end": 872.44
  },
  {
    "key": "passage1",
    "questionKeys": [
      "q16",
      "q17",
      "q18"
    ],
    "start": 917.64,
    "end": 1069.906
  },
  {
    "key": "passage2",
    "questionKeys": [
      "q19",
      "q20",
      "q21"
    ],
    "start": 1086.406,
    "end": 1228.206
  },
  {
    "key": "passage3",
    "questionKeys": [
      "q22",
      "q23",
      "q24",
      "q25"
    ],
    "start": 1244.64,
    "end": 1432.84
  }
]
// July 2020: 16 waveform samples independently verify speed and offset.
const july2020Cues:ListeningClipCue[]=[
  {
    "key": "news1",
    "questionKeys": [
      "q1",
      "q2"
    ],
    "start": 42.899,
    "end": 161.944
  },
  {
    "key": "news2",
    "questionKeys": [
      "q3",
      "q4"
    ],
    "start": 178.778,
    "end": 276.104
  },
  {
    "key": "news3",
    "questionKeys": [
      "q5",
      "q6",
      "q7"
    ],
    "start": 292.411,
    "end": 432.515
  },
  {
    "key": "conversation1",
    "questionKeys": [
      "q8",
      "q9",
      "q10",
      "q11"
    ],
    "start": 481.383,
    "end": 660.012
  },
  {
    "key": "conversation2",
    "questionKeys": [
      "q12",
      "q13",
      "q14",
      "q15"
    ],
    "start": 676.286,
    "end": 845.194
  },
  {
    "key": "passage1",
    "questionKeys": [
      "q16",
      "q17",
      "q18"
    ],
    "start": 894.918,
    "end": 1038.087
  },
  {
    "key": "passage2",
    "questionKeys": [
      "q19",
      "q20",
      "q21"
    ],
    "start": 1054.229,
    "end": 1217.567
  },
  {
    "key": "passage3",
    "questionKeys": [
      "q22",
      "q23",
      "q24",
      "q25"
    ],
    "start": 1233.643,
    "end": 1411.086
  }
]
const recordings:Record<string,ListeningClipCue[]>={
 ...catalogRecordings,
 '117cae76d7a7d8d72e48df57500afd7d9f4fb3c298949dc4f0e27bbaae428fcf':july2020Cues,
 [recordingSha256]:cues,
 '1541d26c964f0711a4e4d5de10682b65321f4181a4ee911ff5505d3ae81f08c1':march2023Cues,
}
export function listeningClipCue(sha256:string|undefined,group:{key:string;questionKeys:string[]}|undefined):ListeningClipCue|null {
 if(!sha256||!group)return null
 return recordings[sha256]?.find(c=>c.key===group.key&&c.questionKeys.length===group.questionKeys.length&&c.questionKeys.every((key,i)=>key===group.questionKeys[i]))||null
}
