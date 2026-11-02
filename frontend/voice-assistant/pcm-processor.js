class Pcm16StreamProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.samples = [];
    this.chunkSize = 1600;
  }

  process(inputs) {
    const channel = inputs[0]?.[0];
    if (!channel) return true;

    const step = sampleRate / 16000;
    for (let position = 0; position < channel.length; position += step) {
      const index = Math.floor(position);
      const fraction = position - index;
      const next = channel[Math.min(index + 1, channel.length - 1)];
      const sample = Math.max(-1, Math.min(1,
        channel[index] * (1 - fraction) + next * fraction));
      this.samples.push(sample < 0
        ? Math.round(sample * 0x8000)
        : Math.round(sample * 0x7fff));
    }

    while (this.samples.length >= this.chunkSize) {
      const chunk = new Int16Array(this.samples.splice(0, this.chunkSize));
      this.port.postMessage(chunk.buffer, [chunk.buffer]);
    }
    return true;
  }
}

registerProcessor('pcm16-stream-processor', Pcm16StreamProcessor);
