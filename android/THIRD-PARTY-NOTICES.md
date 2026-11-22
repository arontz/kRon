# Third-party notices

## shazamio-core

The Shazam fingerprinting in `app/src/main/kotlin/app/kron/lyrics/recognition/shazam/` (the signature format and the
peak-finding algorithm) is a Kotlin port of `src/fingerprinting/algorithm.rs` and `src/fingerprinting/signature_format.rs`
from [shazamio-core](https://github.com/shazamio/shazamio-core), version 2.1.0. That release states that the code derived
from SongRec (GPL-3.0-or-later) in releases up to 2.0.0 was replaced with code written for the crate, and is licensed MIT.
The test vectors in `app/src/test/resources/shazam/` are that crate's own output for two synthetic signals.

```
MIT License

Copyright (c) 2024 dotX12

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
```

The request format (the address and the request body) was read from [ShazamIO](https://github.com/shazamio/ShazamIO)
(MIT). kRon does not use its identity-rotation: it sends its own, truthful User-Agent.
