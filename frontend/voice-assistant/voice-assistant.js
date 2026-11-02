(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('modalNew');
    const modalBody = modal?.querySelector('.modal-body');
    const modalTitle = document.getElementById('modalNewTitle');
    const newProductButton = document.getElementById('btnNew');
    const standaloneAssistantButton = document.getElementById('btnVoiceAssistant');
    const assistantReturnButton = document.getElementById('voiceAssistantReturn');
    const compactAssistantButton = document.getElementById('voiceAssistantCompact');
    const minimizeAssistantButton = document.getElementById('voiceAssistantMinimize');
    const closeAssistantButton = document.getElementById('voiceAssistantClose');
    const expandAssistantButton = document.getElementById('voiceAssistantExpand');
    const restoreAssistantButton = document.getElementById('voiceAssistantRestore');
    const closeBubbleButton = document.getElementById('voiceAssistantBubbleClose');
    const resizeHandles = document.querySelectorAll('#voiceAssistantPanel .voice-assistant-resize');
    const panel = document.getElementById('voiceAssistantPanel');
    const fullPanel = document.getElementById('voiceAssistantFull');
    const assistantHeader = panel?.querySelector('.voice-assistant-floating-header');
    const assistantBrand = document.getElementById('voiceAssistantBrand');
    const bubble = panel?.querySelector('.voice-assistant-bubble');
    const bubblePreview = document.getElementById('voiceAssistantBubblePreview');
    const muteButton = document.getElementById('voiceAssistantMute');
    const bubbleMuteButton = document.getElementById('voiceAssistantBubbleMute');
    const muteLabel = document.getElementById('voiceAssistantMuteLabel');
    const confirmButton = document.getElementById('voiceAssistantConfirm');
    const cancelActionButton = document.getElementById('voiceAssistantCancel');
    const backButton = document.getElementById('voiceAssistantBack');
    const visualizer = document.getElementById('voiceAssistantVisualizer');
    const orbCanvases = Array.from(document.querySelectorAll('.assistant-orb-canvas'));
    const state = document.getElementById('voiceAssistantState');
    const messages = document.getElementById('voiceAssistantMessages');
    const summary = document.getElementById('voiceAssistantSummary');
    if (!modal || !modalBody || !newProductButton || !standaloneAssistantButton
        || !assistantReturnButton || !compactAssistantButton
        || !minimizeAssistantButton || !closeAssistantButton
        || !expandAssistantButton || !restoreAssistantButton || !closeBubbleButton
        || !resizeHandles.length || !fullPanel || !assistantHeader || !assistantBrand
        || !bubble || !bubblePreview
        || !panel || !muteButton || !bubbleMuteButton || !muteLabel
        || !confirmButton || !cancelActionButton || !backButton || !state || !messages || !summary) {
      return;
    }

    let catalogs = null;
    let catalogLoadPromise = null;
    let pendingActionId = null;
    let pendingActionSummary = '';
    let pendingBulkDecision = false;
    let processing = false;
    let socket = null;
    let connectionReady = false;
    let connectionAttemptInProgress = false;
    let connectionTimeout = null;
    let activationRequested = false;
    let keepConnectionWarm = false;
    let reconnectTimer = null;
    let reconnectAttempts = 0;
    let live = false;
    let micMuted = true;
    let audioContext = null;
    let audioWorkletLoaded = false;
    let mediaStream = null;
    let inputSource = null;
    let pcmProcessor = null;
    let silentAudioChunk = null;
    let outputAudioSources = new Set();
    let outputAnalyser = null;
    let outputAnalyserData = null;
    let orbFrame = null;
    let voiceLevel = 0;
    let orbTick = 0;
    let orbMode = 'idle';
    let orbResizeObserver = null;
    const orbModes = {
      idle: { speed: 0.03, noise: 0.15, radius: 0.28, amplitude: 0.028, lines: 3 },
      voice: { speed: 0.18, noise: 0.7, radius: 0.29, amplitude: 0.09, lines: 5 },
      thinking: { speed: 0.08, noise: 0.25, radius: 0.27, amplitude: 0.018, lines: 4 }
    };
    const orbColors = [
      'rgba(203, 236, 255, 0.9)',
      'rgba(0, 218, 255, 0.82)',
      'rgba(42, 112, 255, 0.72)'
    ];
    let nextAudioStart = 0;
    let userMessage = null;
    let assistantMessage = null;
    let suppressModelResponse = false;
    let currentModelTurn = createModelTurn();
    let resumeMicrophoneAfterTurn = false;
    let pendingCanBeVoiceResolved = false;
    let pendingUserSpeechText = '';
    let greetingStarted = false;
    let greetingPending = false;
    let greetingAudioReceived = false;
    let greetingFallbackUsed = false;
    let greetingResponseText = '';
    let greetingTimeout = null;
    let fallbackSpeaking = false;
    let returnToProductForm = false;
    let toolCallQueue = Promise.resolve();
    const queryTools = new Set([
      'search_products', 'get_variant', 'list_catalog', 'recent_movements', 'last_sale'
    ]);

    function updateBubblePreview(text) {
      const preview = String(text || '').replace(/\s+/g, ' ').trim();
      if (preview) bubblePreview.textContent = preview;
    }

    function addMessage(text, kind) {
      const item = document.createElement('div');
      item.className = `voice-assistant-message voice-assistant-message-${kind}`;
      item.textContent = text;
      messages.appendChild(item);
      messages.scrollTop = messages.scrollHeight;
      updateBubblePreview(text);
      if (kind === 'error') setStatus(text, true);
      return item;
    }

    function appendMessageText(element, text, kind) {
      if (!text) return element;
      const target = element || addMessage('', kind || 'assistant');
      target.textContent += text;
      messages.scrollTop = messages.scrollHeight;
      updateBubblePreview(target.textContent);
      return target;
    }

    function setBusy(busy, message) {
      processing = busy;
      muteButton.disabled = busy;
      bubbleMuteButton.disabled = busy;
      confirmButton.disabled = busy || !pendingActionId;
      cancelActionButton.disabled = busy || !pendingActionId;
      updateVisualizer();
      if (message) setStatus(message);
    }

    async function prepareAudioContext() {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (!AudioContextClass) return;
      if (!audioContext || audioContext.state === 'closed') {
        audioContext = new AudioContextClass();
        audioWorkletLoaded = false;
      }
      await audioContext.resume();
    }

    function setStatus(message, isError = false) {
      state.textContent = message;
      state.classList.toggle('is-error', isError);
    }

    function createModelTurn() {
      return { transcript: '', text: '', audioReceived: false };
    }

    function modelTurnText(turn) {
      return turn.transcript.trim() || turn.text.trim();
    }

    function completeGreeting(useFallback = false) {
      if (!greetingPending) return;
      greetingPending = false;
      window.clearTimeout(greetingTimeout);
      greetingTimeout = null;

      if (useFallback && !greetingAudioReceived) {
        const text = greetingResponseText.trim()
          || 'Hola, ya te estoy escuchando. ¿En qué te puedo ayudar?';
        if (window.speechSynthesis && window.SpeechSynthesisUtterance) {
          greetingFallbackUsed = true;
          const utterance = new window.SpeechSynthesisUtterance(text);
          utterance.lang = 'es-CO';
          fallbackSpeaking = true;
          updateVisualizer();
          const finishFallback = () => {
            fallbackSpeaking = false;
            updateVisualizer();
            setStatus('Escuchando… habla con naturalidad');
          };
          utterance.onend = finishFallback;
          utterance.onerror = finishFallback;
          window.speechSynthesis.cancel();
          window.speechSynthesis.speak(utterance);
          setStatus('El audio de bienvenida está tardando; reproduciendo saludo alternativo…');
          return;
        }
        appendMessageText(null, text, 'assistant');
      }

      setStatus('Escuchando… habla con naturalidad');
    }

    function startGreeting() {
      if (greetingStarted || !socket || socket.readyState !== WebSocket.OPEN) return;
      greetingStarted = true;
      greetingPending = true;
      greetingAudioReceived = false;
      greetingFallbackUsed = false;
      greetingResponseText = '';
      setStatus('Micrófono activo. Iniciando conversación…');
      const instruction = pendingActionId
        ? `Saluda ahora en español, confirma que ya estás escuchando y continúa leyendo este cambio pendiente; espera una confirmación o cancelación explícita: ${pendingActionSummary}`
        : 'Inicia la conversación ahora con un saludo breve en español. Confirma que ya estás escuchando y pregunta en qué puedes ayudar.';
      socket.send(JSON.stringify({ realtimeInput: { text: instruction } }));
      greetingTimeout = window.setTimeout(() => completeGreeting(true), 12000);
    }

    function updateMuteControl() {
      muteButton.setAttribute('aria-pressed', String(micMuted));
      muteLabel.textContent = micMuted ? 'Activar micrófono' : 'Silenciar micrófono';
      muteButton.setAttribute('aria-label', micMuted ? 'Activar micrófono' : 'Silenciar micrófono');
      muteButton.classList.toggle('is-muted', micMuted);
      bubbleMuteButton.setAttribute('aria-pressed', String(micMuted));
      bubbleMuteButton.setAttribute('aria-label',
        micMuted ? 'Activar micrófono' : 'Silenciar micrófono');
      bubbleMuteButton.title = micMuted ? 'Activar micrófono' : 'Silenciar micrófono';
      bubbleMuteButton.classList.toggle('is-muted', micMuted);
    }

    function updateVisualizer() {
      const assistantSpeaking = fallbackSpeaking || outputAudioSources.size > 0;
      orbMode = assistantSpeaking ? 'voice' : processing ? 'thinking' : 'idle';
      visualizer?.classList.toggle('is-active', assistantSpeaking);
      panel.classList.toggle('is-speaking', assistantSpeaking);
      orbCanvases.forEach(canvas => {
        canvas.parentElement.dataset.mode = orbMode;
      });
      voiceLevel = assistantSpeaking ? voiceLevel : 0;
      scheduleOrbFrame();
    }

    function scheduleOrbFrame() {
      if (orbFrame === null && !document.hidden && !panel.hidden) {
        orbFrame = window.requestAnimationFrame(renderOrbFrame);
      }
    }

    function renderOrbFrame(timestamp) {
      orbFrame = null;
      if (document.hidden || panel.hidden) return;

      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const mode = orbModes[orbMode];
      orbTick += reducedMotion ? 0 : mode.speed;

      if (orbMode === 'voice' && outputAnalyser && outputAnalyserData && outputAudioSources.size > 0) {
        outputAnalyser.getByteTimeDomainData(outputAnalyserData);
        let sumSquares = 0;
        for (const sample of outputAnalyserData) {
          const amplitude = (sample - 128) / 128;
          sumSquares += amplitude * amplitude;
        }
        const rms = Math.sqrt(sumSquares / outputAnalyserData.length);
        voiceLevel += (Math.min(1, rms * 5) - voiceLevel) * 0.38;
      } else if (orbMode === 'voice' && fallbackSpeaking) {
        voiceLevel = 0.24 + 0.18 * (0.5 + 0.5 * Math.sin(timestamp / 110));
      } else if (orbMode !== 'voice') {
        voiceLevel = 0;
      }

      orbCanvases.forEach(canvas => drawOrb(canvas, mode, reducedMotion));
      scheduleOrbFrame();
    }

    function drawOrb(canvas, mode, reducedMotion) {
      const context = canvas.getContext('2d');
      if (!context) return;
      const width = Number(canvas.dataset.width);
      const height = Number(canvas.dataset.height);
      if (!width || !height) return;

      context.clearRect(0, 0, width, height);
      const centerX = width / 2;
      const centerY = height / 2;
      const baseRadius = Math.min(width, height) * mode.radius;
      const voiceIntensity = orbMode === 'voice' ? 0.25 + voiceLevel * 0.75 : 1;
      const amplitude = reducedMotion ? 0 : mode.amplitude * voiceIntensity;
      for (let layer = 0; layer < mode.lines; layer += 1) {
        const offset = orbTick + layer * 18.5;
        const radius = baseRadius - layer * Math.min(width, height) * 0.025;
        const layerAmplitude = amplitude * (1 - layer * 0.12);
        context.beginPath();
        for (let point = 0; point <= 80; point += 1) {
          const angle = point / 80 * Math.PI * 2;
          const harmonic = Math.sin(angle * 4 + offset * 1.5)
            * Math.cos(angle * 2 - offset);
          const microNoise = Math.sin(angle * 12 + offset * 3) * mode.noise * 0.3;
          const currentRadius = radius + (harmonic + microNoise) * layerAmplitude * Math.min(width, height);
          const x = centerX + Math.cos(angle) * currentRadius;
          const y = centerY + Math.sin(angle) * currentRadius;
          if (point === 0) context.moveTo(x, y);
          else context.lineTo(x, y);
        }
        context.closePath();
        context.strokeStyle = orbColors[layer % orbColors.length];
        context.lineWidth = Math.max(0.9, Math.min(width, height) * 0.006);
        context.stroke();
      }
    }

    function sizeOrbCanvases() {
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
      orbCanvases.forEach(canvas => {
        const bounds = canvas.getBoundingClientRect();
        const width = bounds.width;
        const height = bounds.height;
        if (!width || !height) return;
        canvas.width = Math.round(width * pixelRatio);
        canvas.height = Math.round(height * pixelRatio);
        const context = canvas.getContext('2d');
        if (!context) return;
        context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
        canvas.dataset.width = String(width);
        canvas.dataset.height = String(height);
      });
      scheduleOrbFrame();
    }

    if ('ResizeObserver' in window) {
      orbResizeObserver = new ResizeObserver(sizeOrbCanvases);
      orbCanvases.forEach(canvas => {
        if (canvas.parentElement) orbResizeObserver.observe(canvas.parentElement);
      });
    } else {
      window.addEventListener('resize', sizeOrbCanvases);
    }
    document.addEventListener('visibilitychange', scheduleOrbFrame);
    sizeOrbCanvases();

    function renderSummary(text) {
      summary.textContent = text || '';
      summary.hidden = !text;
      pendingActionSummary = text || '';
      confirmButton.hidden = !pendingActionId;
      confirmButton.disabled = processing || !pendingActionId;
      cancelActionButton.hidden = !pendingActionId;
      cancelActionButton.disabled = processing || !pendingActionId;
      muteButton.disabled = processing;
      bubbleMuteButton.disabled = processing;
    }

    function loadCatalogs() {
      if (catalogs) return Promise.resolve();
      if (!catalogLoadPromise) {
        const endpoints = [
          'GetCategorias', 'GetGeneros', 'GetColores', 'GetMarcas', 'GetTallas', 'GetEstilosUnicos'
        ];
        catalogLoadPromise = Promise.all(endpoints.map(endpoint =>
          fetch(`${window.API_BASE || ''}/${endpoint}`, { credentials: 'include' })
        )).then(responses => {
          if (responses.some(response => !response.ok)) {
            throw new Error('No se pudieron cargar las opciones de producto.');
          }
          return Promise.all(responses.map(response => response.json()));
        }).then(([categories, genders, colors, brands, sizes, styles]) => {
          catalogs = { categories, genders, colors, brands, sizes, styles };
        }).catch(error => {
          catalogLoadPromise = null;
          throw error;
        });
      }
      return catalogLoadPromise;
    }

    async function restorePendingAction() {
      const response = await fetch(`${window.API_BASE || ''}/api/voice-assistant/pending`, {
        credentials: 'include'
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'No se pudo revisar el cambio pendiente.');
      pendingActionId = result.pending ? result.actionId : null;
      pendingBulkDecision = Boolean(result.pendingBulkDecision);
      pendingCanBeVoiceResolved = false;
      pendingUserSpeechText = '';
      renderSummary(result.pending ? result.actionSummary : '');
    }

    async function openAssistant() {
      if (modal.style.display !== 'none') {
        returnToProductForm = true;
        modal.style.display = 'none';
      }
      panel.hidden = false;
      panel.classList.add('is-open');
      panel.classList.remove('is-collapsed');
      panel.classList.remove('is-compact');
      updateCompactControl(false);
      resetPanelPosition();
      fullPanel.hidden = false;
      bubble.hidden = true;
      if (!messages.childElementCount) updateBubblePreview('Conectando…');
      assistantReturnButton.hidden = true;
      standaloneAssistantButton.setAttribute('aria-expanded', 'true');
      backButton.hidden = !returnToProductForm;
      setBusy(true, 'Preparando conversación…');
      loadCatalogs().catch(error => {
        if (!panel.hidden) {
          addMessage(`No se pudieron precargar los catálogos; el asistente los consultará cuando haga falta: ${error.message}`, 'error');
        }
      });
      try {
        await Promise.all([
          prepareAudioContext(),
          restorePendingAction()
        ]);
        setBusy(false, pendingActionId
          ? 'Puedes confirmar o cancelar por voz o con los botones.'
          : pendingBulkDecision
            ? 'Hay presentaciones repetidas. Indica si agregas sus cantidades al stock o las excluyes.'
            : '');
        activationRequested = true;
        await connectLive(true);
      } catch (error) {
        setBusy(false);
        addMessage(error.message, 'error');
      }
    }

    function showManualForm() {
      panel.hidden = true;
      panel.classList.remove('is-open');
      assistantReturnButton.hidden = false;
      standaloneAssistantButton.setAttribute('aria-expanded', 'false');
      returnToProductForm = false;
      modal.style.display = 'flex';
    }

    function hideAssistant() {
      panel.hidden = true;
      panel.classList.remove('is-open');
      standaloneAssistantButton.setAttribute('aria-expanded', 'false');
    }

    function minimizeAssistant() {
      panel.classList.add('is-collapsed');
      panel.classList.remove('is-compact');
      resetPanelPosition();
      updateCompactControl(false);
      fullPanel.hidden = true;
      bubble.hidden = false;
      standaloneAssistantButton.setAttribute('aria-expanded', 'false');
    }

    function expandAssistant() {
      panel.classList.remove('is-collapsed');
      panel.classList.remove('is-compact');
      resetPanelPosition();
      updateCompactControl(false);
      fullPanel.hidden = false;
      bubble.hidden = true;
      standaloneAssistantButton.setAttribute('aria-expanded', 'true');
    }

    function closeAssistant() {
      const returnToForm = returnToProductForm;
      activationRequested = false;
      stopMicrophone();
      stopOutputAudio();
      if (fallbackSpeaking) window.speechSynthesis?.cancel();
      fallbackSpeaking = false;
      if (audioContext && audioContext.state !== 'closed') {
        audioContext.close();
        audioContext = null;
      }
      messages.replaceChildren();
      userMessage = null;
      assistantMessage = null;
      pendingUserSpeechText = '';
      pendingCanBeVoiceResolved = false;
      resumeMicrophoneAfterTurn = false;
      returnToProductForm = false;
      panel.hidden = true;
      panel.classList.remove('is-open', 'is-collapsed');
      panel.classList.remove('is-compact');
      updateCompactControl(false);
      fullPanel.hidden = false;
      bubble.hidden = true;
      standaloneAssistantButton.setAttribute('aria-expanded', 'false');
      updateBubblePreview('Conversación cerrada');
      if (returnToForm) showManualForm();
    }

    function updateCompactControl(compact) {
      compactAssistantButton.setAttribute('aria-pressed', String(compact));
      compactAssistantButton.setAttribute('aria-label',
        compact ? 'Vista completa' : 'Vista compacta');
      compactAssistantButton.title = compact ? 'Vista completa' : 'Vista compacta';
      compactAssistantButton.textContent = compact ? '▣' : '□';
    }

    function toggleCompactAssistant() {
      const compact = !panel.classList.contains('is-compact');
      panel.classList.toggle('is-compact', compact);
      updateCompactControl(compact);
    }

    function resetPanelPosition() {
      panel.style.removeProperty('left');
      panel.style.removeProperty('top');
      panel.style.removeProperty('right');
      panel.style.removeProperty('bottom');
    }

    function movePanel(event) {
      if (event.button !== 0 || panel.classList.contains('is-compact')
          || panel.classList.contains('is-collapsed')
          || event.target.closest('.voice-assistant-window-actions')
          || event.target.closest('.voice-assistant-brand')) return;
      event.preventDefault();
      const bounds = panel.getBoundingClientRect();
      const start = { pointerId: event.pointerId, x: event.clientX, y: event.clientY,
        left: bounds.left, top: bounds.top };
      assistantHeader.setPointerCapture(event.pointerId);
      const onMove = moveEvent => {
        if (moveEvent.pointerId !== start.pointerId) return;
        panel.style.left = `${Math.max(12, Math.min(window.innerWidth - bounds.width - 12,
          start.left + moveEvent.clientX - start.x))}px`;
        panel.style.top = `${Math.max(12, Math.min(window.innerHeight - bounds.height - 12,
          start.top + moveEvent.clientY - start.y))}px`;
        panel.style.right = 'auto';
        panel.style.bottom = 'auto';
      };
      const onEnd = endEvent => {
        if (endEvent.pointerId !== start.pointerId) return;
        assistantHeader.removeEventListener('pointermove', onMove);
        assistantHeader.removeEventListener('pointerup', onEnd);
        assistantHeader.removeEventListener('pointercancel', onEnd);
        if (assistantHeader.hasPointerCapture(endEvent.pointerId)) {
          assistantHeader.releasePointerCapture(endEvent.pointerId);
        }
      };
      assistantHeader.addEventListener('pointermove', onMove);
      assistantHeader.addEventListener('pointerup', onEnd);
      assistantHeader.addEventListener('pointercancel', onEnd);
    }

    function resizePanel(event) {
      const direction = event.currentTarget.dataset.direction;
      if (event.button !== 0 || !direction || panel.classList.contains('is-compact')
          || panel.classList.contains('is-collapsed')) return;
      event.preventDefault();
      const bounds = panel.getBoundingClientRect();
      const start = {
        pointerId: event.pointerId,
        x: event.clientX,
        y: event.clientY,
        left: bounds.left,
        top: bounds.top,
        width: bounds.width,
        height: bounds.height
      };
      const handle = event.currentTarget;
      handle.setPointerCapture(event.pointerId);

      const onMove = moveEvent => {
        if (moveEvent.pointerId !== start.pointerId) return;
        const maxViewportWidth = Math.max(1, window.innerWidth - 24);
        const heightLimit = window.matchMedia('(max-width: 600px)').matches
          ? window.innerHeight - 24
          : Math.min(window.innerHeight * 0.78, 680);
        const maxHeight = Math.max(1, heightLimit);
        const horizontalDelta = moveEvent.clientX - start.x;
        const verticalDelta = moveEvent.clientY - start.y;
        const west = direction.includes('w');
        const north = direction.includes('n');
        const maxWidth = Math.min(maxViewportWidth, west
          ? start.left + start.width - 12
          : window.innerWidth - start.left - 12);
        const maxAllowedHeight = Math.min(maxHeight, north
          ? start.top + start.height - 12
          : window.innerHeight - start.top - 12);
        const minWidth = Math.min(300, maxWidth);
        const minHeight = Math.min(320, maxAllowedHeight);
        const width = Math.min(maxWidth, Math.max(minWidth,
          start.width + (direction.includes('e') ? horizontalDelta : west ? -horizontalDelta : 0)));
        const height = Math.min(maxAllowedHeight, Math.max(minHeight,
          start.height + (direction.includes('s') ? verticalDelta : north ? -verticalDelta : 0)));
        const left = west ? start.left + start.width - width : start.left;
        const top = north ? start.top + start.height - height : start.top;
        panel.style.setProperty('--assistant-width', `${width}px`);
        panel.style.setProperty('--assistant-height', `${height}px`);
        panel.style.left = `${left}px`;
        panel.style.top = `${top}px`;
        panel.style.right = 'auto';
        panel.style.bottom = 'auto';
      };
      const onEnd = endEvent => {
        if (endEvent.pointerId !== start.pointerId) return;
        handle.removeEventListener('pointermove', onMove);
        handle.removeEventListener('pointerup', onEnd);
        handle.removeEventListener('pointercancel', onEnd);
        if (handle.hasPointerCapture(endEvent.pointerId)) {
          handle.releasePointerCapture(endEvent.pointerId);
        }
      };
      handle.addEventListener('pointermove', onMove);
      handle.addEventListener('pointerup', onEnd);
      handle.addEventListener('pointercancel', onEnd);
    }

    function toBase64(buffer) {
      const bytes = new Uint8Array(buffer);
      let binary = '';
      const blockSize = 0x8000;
      for (let offset = 0; offset < bytes.length; offset += blockSize) {
        binary += String.fromCharCode(...bytes.subarray(offset, offset + blockSize));
      }
      return btoa(binary);
    }

    function fromBase64(value) {
      const binary = atob(value);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
      }
      return bytes;
    }

    async function readLiveMessage(data) {
      if (typeof data === 'string') return data;
      if (data instanceof Blob) return data.text();
      if (data instanceof ArrayBuffer) return new TextDecoder().decode(data);
      throw new Error(`Formato de mensaje no soportado: ${Object.prototype.toString.call(data)}`);
    }

    async function connectLive(activate = false) {
      if (activate) activationRequested = true;
      if (connectionReady) {
        if (activationRequested) activateLive();
        return;
      }
      if (socket || connectionAttemptInProgress || (processing && !activate)) return;
      if (activate && (!navigator.mediaDevices?.getUserMedia || !window.AudioWorkletNode
          || !(window.AudioContext || window.webkitAudioContext))) {
        addMessage('Este navegador no permite audio en vivo. Prueba con una versión reciente de Chrome o Edge.', 'error');
        return;
      }

      if (activate) setBusy(true, 'Conectando con Gemini Live…');
      connectionAttemptInProgress = true;
      const sessionRequest = new AbortController();
      const sessionRequestTimeout = window.setTimeout(() => sessionRequest.abort(), 15000);
      try {
        const response = await fetch(`${window.API_BASE || ''}/api/voice-assistant/live-session`, {
          method: 'POST',
          credentials: 'include',
          signal: sessionRequest.signal
        });
        window.clearTimeout(sessionRequestTimeout);
        const credentials = await response.json();
        if (!response.ok || !credentials.token || !credentials.setup) {
          throw new Error(credentials.error || 'No se pudo iniciar Gemini Live.');
        }

        const setup = JSON.parse(JSON.stringify(credentials.setup));
        if (catalogs) {
          const catalogContext = `\nCatálogos actuales (solo referencia; el backend valida cada cambio): ${JSON.stringify(catalogs)}`;
          setup.systemInstruction.parts[0].text += catalogContext;
        }
        const endpoint = 'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained';
        const connection = new WebSocket(`${endpoint}?access_token=${encodeURIComponent(credentials.token)}`);
        connection.binaryType = 'arraybuffer';
        socket = connection;
        connectionAttemptInProgress = false;
        connectionTimeout = window.setTimeout(() => {
          failLiveConnection(connection,
            'Gemini Live no terminó de configurar el canal. Reintentando conexión…');
        }, 15000);
        connection.addEventListener('open', () => {
          try {
            connection.send(JSON.stringify({ setup }));
            setStatus('Configurando conversación…');
          } catch (error) {
            failLiveConnection(connection,
              `No se pudo enviar la configuración al canal de voz: ${error.message}`);
          }
        });
        connection.addEventListener('message', async event => {
          let message;
          try {
            const payload = await readLiveMessage(event.data);
            message = JSON.parse(payload);
          } catch (error) {
            if (socket !== connection) return;
            const reason = error instanceof Error ? error.message : 'formato desconocido';
            addMessage(`No se pudo interpretar la respuesta de Gemini Live: ${reason}`, 'error');
            return;
          }
          if (socket !== connection) return;
          handleLiveMessage(message);
        });
        connection.addEventListener('error', () => {
          failLiveConnection(connection,
            'No se pudo establecer el canal de Gemini Live. Reintentando conexión…');
        });
        connection.addEventListener('close', event => {
          if (socket !== connection) return;
          const resumeVoice = activationRequested;
          window.clearTimeout(connectionTimeout);
          connectionTimeout = null;
          stopMicrophone();
          connectionReady = false;
          activationRequested = resumeVoice;
          live = false;
          socket = null;
          setBusy(false);
          if (event.code !== 1000 && event.code !== 1005) {
            const reason = event.reason ? `: ${event.reason.slice(0, 180)}` : '';
            addMessage(`Gemini cerró la conversación (código ${event.code})${reason}`, 'error');
          } else if (!event.wasClean) {
            setStatus('La conexión se cerró inesperadamente. Puedes reintentar con el micrófono.', true);
          } else if (!pendingActionId) {
            setStatus('El canal de voz se cerró; reconectando en segundo plano.');
          }
          scheduleWarmReconnect();
        });
      } catch (error) {
        window.clearTimeout(sessionRequestTimeout);
        connectionAttemptInProgress = false;
        if (activate) {
          const reason = error.name === 'AbortError'
            ? 'El backend tardó demasiado en preparar la sesión de voz.'
            : error.message;
          setBusy(false, `No se pudo iniciar Gemini Live: ${reason}`);
          addMessage(reason, 'error');
        }
        scheduleWarmReconnect();
      }
    }

    function failLiveConnection(connection, message) {
      if (socket !== connection) return;
      window.clearTimeout(connectionTimeout);
      connectionTimeout = null;
      socket = null;
      connectionAttemptInProgress = false;
      connectionReady = false;
      stopMicrophone();
      if (connection.readyState < WebSocket.CLOSING) connection.close();
      setBusy(false, message);
      scheduleWarmReconnect();
    }

    async function activateLive() {
      if (!activationRequested || !connectionReady || live) return;
      try {
        await prepareAudioContext();
        await startMicrophone();
        startGreeting();
      } catch (error) {
        activationRequested = false;
        stopMicrophone();
        setBusy(false, 'No se pudo activar el micrófono');
        addMessage(error.name === 'NotAllowedError'
          ? 'Permite el acceso al micrófono e inténtalo de nuevo.'
          : `No se pudo iniciar el audio en vivo: ${error.message}`, 'error');
      }
    }

    async function warmAssistantConnection() {
      if (!keepConnectionWarm || socket || connectionReady) return;
      const sessionResponse = await fetch(`${window.API_BASE || ''}/CheckSession`, {
        credentials: 'include'
      });
      if (sessionResponse.status === 401) {
        keepConnectionWarm = false;
        return;
      }
      if (!sessionResponse.ok) {
        throw new Error('No se pudo validar la sesión para preparar el asistente.');
      }
      await connectLive(false);
    }

    function scheduleWarmReconnect() {
      if (!keepConnectionWarm || reconnectTimer !== null) return;
      const delay = Math.min(1000 * (2 ** reconnectAttempts), 8000);
      reconnectAttempts += 1;
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null;
        warmAssistantConnection().catch(error => {
          if (!panel.hidden) {
            setStatus(`No se pudo preparar el canal de voz: ${error.message}`, true);
          }
          scheduleWarmReconnect();
        });
      }, delay);
    }

    async function startMicrophone() {
      if (!socket || socket.readyState !== WebSocket.OPEN || live) return;
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (!audioContext || audioContext.state === 'closed') {
        audioContext = new AudioContextClass();
        audioWorkletLoaded = false;
      }
      await audioContext.resume();
      if (!audioWorkletLoaded) {
        await audioContext.audioWorklet.addModule('voice-assistant/pcm-processor.js');
        audioWorkletLoaded = true;
      }
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      inputSource = audioContext.createMediaStreamSource(mediaStream);
      pcmProcessor = new AudioWorkletNode(audioContext, 'pcm16-stream-processor');
      const mute = audioContext.createGain();
      mute.gain.value = 0;
      pcmProcessor.port.onmessage = event => {
        if (!socket || socket.readyState !== WebSocket.OPEN) return;
        const audioBuffer = micMuted
          ? (silentAudioChunk ||= new Int16Array(new Int16Array(event.data).length).buffer)
          : event.data;
        socket.send(JSON.stringify({
          realtimeInput: {
            audio: {
              data: toBase64(audioBuffer),
              mimeType: 'audio/pcm;rate=16000'
            }
          }
        }));
      };
      inputSource.connect(pcmProcessor);
      pcmProcessor.connect(mute);
      mute.connect(audioContext.destination);
      live = true;
      micMuted = false;
      updateMuteControl();
      setBusy(false, 'Escuchando… habla con naturalidad');
    }

    function stopMicrophone() {
      inputSource?.disconnect();
      pcmProcessor?.disconnect();
      inputSource = null;
      pcmProcessor = null;
      if (mediaStream) {
        mediaStream.getTracks().forEach(track => track.stop());
        mediaStream = null;
      }
      micMuted = true;
      updateMuteControl();
      updateVisualizer();
      live = false;
    }

    function stopOutputAudio() {
      outputAudioSources.forEach(source => {
        try {
          source.stop();
        } catch {
          // A source can finish between turns.
        }
      });
      outputAudioSources.clear();
      updateVisualizer();
      if (audioContext) nextAudioStart = audioContext.currentTime;
    }

    function speakFallback(text) {
      const spokenText = String(text || '').trim();
      if (!spokenText) return false;
      if (!window.speechSynthesis || !window.SpeechSynthesisUtterance) {
        setStatus('El navegador no ofrece síntesis de voz; la respuesta está en la conversación.', true);
        return false;
      }

      const resumeMicrophone = live && !micMuted;
      stopOutputAudio();
      window.speechSynthesis.cancel();
      if (resumeMicrophone) {
        micMuted = true;
        updateMuteControl();
      }

      const utterance = new window.SpeechSynthesisUtterance(spokenText);
      utterance.lang = 'es-CO';
      const restoreMicrophone = () => {
        fallbackSpeaking = false;
        updateVisualizer();
        if (resumeMicrophone && live) {
          micMuted = false;
          updateMuteControl();
        }
        setStatus('Escuchando… habla con naturalidad');
      };
      fallbackSpeaking = true;
      updateVisualizer();
      utterance.onend = restoreMicrophone;
      utterance.onerror = event => {
        restoreMicrophone();
        addMessage(`No se pudo reproducir la respuesta de voz alternativa: ${event.error || 'error de síntesis'}`, 'error');
      };
      window.speechSynthesis.speak(utterance);
      return true;
    }

    async function playAudio(encodedAudio) {
      if (!audioContext) throw new Error('El dispositivo de audio no está disponible.');
      if (audioContext.state !== 'running') await audioContext.resume();
      if (audioContext.state !== 'running') {
        throw new Error('El navegador mantiene suspendida la salida de audio.');
      }
      const bytes = fromBase64(encodedAudio);
      const samples = new Int16Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.byteLength / 2));
      const buffer = audioContext.createBuffer(1, samples.length, 24000);
      const channel = buffer.getChannelData(0);
      for (let index = 0; index < samples.length; index += 1) {
        channel[index] = samples[index] / 32768;
      }
      const source = audioContext.createBufferSource();
      source.buffer = buffer;
      if (!outputAnalyser || outputAnalyser.context !== audioContext) {
        outputAnalyser = audioContext.createAnalyser();
        outputAnalyser.fftSize = 256;
        outputAnalyser.smoothingTimeConstant = 0.72;
        outputAnalyserData = new Uint8Array(outputAnalyser.fftSize);
        outputAnalyser.connect(audioContext.destination);
      }
      source.connect(outputAnalyser);
      const startAt = Math.max(audioContext.currentTime, nextAudioStart);
      source.start(startAt);
      nextAudioStart = startAt + buffer.duration;
      outputAudioSources.add(source);
      updateVisualizer();
      source.addEventListener('ended', () => {
        outputAudioSources.delete(source);
        updateVisualizer();
      }, { once: true });
    }

    function handleLiveMessage(message) {
      if (message.error) {
        const detail = message.error.message || message.error.status || 'Gemini rechazó la configuración de voz.';
        const resumeVoice = activationRequested;
        addMessage(`Gemini Live: ${detail}`, 'error');
        disconnectLive();
        activationRequested = resumeVoice;
        setBusy(false, 'Gemini rechazó la configuración. Reintentando conexión…');
        scheduleWarmReconnect();
        return;
      }
      if (message.setupComplete) {
        window.clearTimeout(connectionTimeout);
        connectionTimeout = null;
        connectionReady = true;
        reconnectAttempts = 0;
        if (activationRequested) {
          activateLive();
        } else if (!panel.hidden) {
          setBusy(false, 'Canal listo. Activa el micrófono cuando quieras hablar.');
        }
      }
      if (message.serverContent) {
        const content = message.serverContent;
        const responseTurn = currentModelTurn;
        if (content.inputTranscription?.text) {
          greetingFallbackUsed = false;
          if (pendingActionId || pendingBulkDecision) {
            pendingCanBeVoiceResolved = true;
            pendingUserSpeechText = `${pendingUserSpeechText} ${content.inputTranscription.text}`.trim();
          }
          userMessage = appendMessageText(userMessage, content.inputTranscription.text, 'user');
        }
        if (!suppressModelResponse && content.outputTranscription?.text) {
          if (greetingPending) greetingResponseText += content.outputTranscription.text;
          responseTurn.transcript += content.outputTranscription.text;
          assistantMessage = appendMessageText(assistantMessage, content.outputTranscription.text);
        }
        for (const part of suppressModelResponse ? [] : content.modelTurn?.parts || []) {
          if (part.inlineData?.data && !greetingFallbackUsed) {
            responseTurn.audioReceived = true;
            if (greetingPending) {
              greetingAudioReceived = true;
              window.clearTimeout(greetingTimeout);
              greetingTimeout = null;
            }
            playAudio(part.inlineData.data).catch(error => {
              addMessage(`No se pudo reproducir el audio del asistente: ${error.message}. Intentaré usar voz alternativa.`, 'error');
              if (greetingPending) {
                greetingAudioReceived = false;
                completeGreeting(true);
              } else {
                speakFallback(modelTurnText(responseTurn));
              }
            });
          }
          if (part.text) {
            responseTurn.text += part.text;
            assistantMessage = appendMessageText(assistantMessage, part.text);
          }
        }
        if (content.interrupted) stopOutputAudio();
        if (content.turnComplete) {
          if (greetingPending) completeGreeting(true);
          else if (!suppressModelResponse && !responseTurn.audioReceived) {
            speakFallback(modelTurnText(responseTurn));
          }
          greetingFallbackUsed = false;
          suppressModelResponse = false;
          currentModelTurn = createModelTurn();
          userMessage = null;
          assistantMessage = null;
          if (resumeMicrophoneAfterTurn && !pendingActionId) {
            resumeMicrophoneAfterTurn = false;
            const delay = audioContext
              ? Math.max(0, (nextAudioStart - audioContext.currentTime) * 1000)
              : 0;
            window.setTimeout(
              () => startMicrophone().catch(error => addMessage(error.message, 'error')),
              delay);
          }
        }
      }
      if (message.toolCall) {
        toolCallQueue = toolCallQueue
          .then(() => handleToolCall(message.toolCall))
          .catch(error => addMessage(error.message, 'error'));
      }
    }

    async function handleToolCall(toolCall) {
      const functionResponses = [];
      for (const call of toolCall.functionCalls || []) {
        let response;
        const isPendingResolution = call.name === 'confirm_pending_action'
          || call.name === 'cancel_pending_action'
          || call.name === 'resolve_bulk_duplicates';
        const voiceDecision = pendingUserSpeechText
          .normalize('NFD')
          .replace(/\p{Diacritic}/gu, '')
          .toLowerCase()
          .replace(/[¿?¡!,.;:]/g, ' ')
          .replace(/\s+/g, ' ')
          .trim();
        const affirmative = /^(si|ok|okay|esta bien|perfecto|listo|vale|claro|de acuerdo|hazlo|adelante|procede|confirmo|confirmar|acepto|dale|correcto|me parece bien|puedes hacerlo)( (por favor|porfa|adelante|hazlo|confirmo|dale|perfecto|esta bien|procede|por mi))*$/.test(voiceDecision);
        const negative = /^(no|cancela|cancelar|rechazo|no lo hagas|mejor no|no estoy de acuerdo)( por favor| cancela| cancelar| lo hagas)?$/.test(voiceDecision);
        const addStockIntent = /\b(agrega(r|le|las|los|las al|los al)?|sum(a|ar|arle|arle al|alas|alos)|ingresa(r|le|las|los)?|anade|aumenta(r|le|las|los)?|al stock|al inventario)\b/.test(voiceDecision);
        const omitIntent = /\b(omite|omitir|excluye|excluir|salta|saltar|no agregues|no las agregues|no los agregues|dejalo por fuera|dejalas por fuera)\b/.test(voiceDecision);
        const conditional = /\b(tal vez|quizas|quiza|depende|pero|aunque|si pudiera|si puedes|si se puede)\b/.test(voiceDecision);
        const explicitDecision = call.name === 'confirm_pending_action'
          ? affirmative
          : call.name === 'cancel_pending_action'
            ? negative
            : pendingBulkDecision && !conditional && addStockIntent !== omitIntent
              && ((call.args?.decision === 'add_stock' && addStockIntent)
                || (call.args?.decision === 'omit' && omitIntent));
        if (isPendingResolution && (!pendingCanBeVoiceResolved || !explicitDecision)) {
          pendingUserSpeechText = '';
          response = {
            id: call.id,
            name: call.name,
            response: {
              error: call.name === 'resolve_bulk_duplicates'
                ? 'La respuesta no eligió claramente agregar las cantidades al stock o excluir las presentaciones. Pregunta de nuevo sin ejecutar el lote.'
                : 'La voz del usuario no confirmó explícitamente esta acción. Lee el resumen y espera una respuesta afirmativa o cancelación clara.'
            }
          };
          functionResponses.push(response);
          continue;
        }
        try {
          const result = await fetch(`${window.API_BASE || ''}/api/voice-assistant/tools`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: call.name, arguments: call.args || {} })
          }).then(async httpResponse => {
            const body = await httpResponse.json();
            if (!httpResponse.ok || !body.ok) {
              throw new Error(body.error || 'No se pudo ejecutar la consulta.');
            }
            return body;
          });
          if (result.actionId) {
            pendingActionId = result.actionId;
            pendingBulkDecision = false;
            pendingCanBeVoiceResolved = false;
            pendingUserSpeechText = '';
            renderSummary(result.actionSummary);
            state.textContent = 'Cambio pendiente. Responde con una afirmación clara o cancélalo; también puedes usar los botones.';
          } else if (result.needsDuplicateDecision) {
            pendingBulkDecision = true;
            pendingCanBeVoiceResolved = false;
            pendingUserSpeechText = '';
            state.textContent = 'Indica si agregas las cantidades al stock o excluyes las presentaciones repetidas.';
          } else if (call.name === 'resolve_bulk_duplicates') {
            pendingBulkDecision = false;
            pendingCanBeVoiceResolved = false;
            pendingUserSpeechText = '';
          } else if (call.name === 'confirm_pending_action') {
            pendingActionId = null;
            pendingBulkDecision = false;
            pendingCanBeVoiceResolved = false;
            pendingUserSpeechText = '';
            renderSummary('');
            window.showToast('Cambio aplicado correctamente', 'success');
            window.cargarProductos?.().catch(error => addMessage(
              `El cambio se aplicó, pero no se pudo actualizar el catálogo: ${error.message}`,
              'error'));
            setStatus('Cambio aplicado. Puedes seguir consultando el inventario.');
          } else if (call.name === 'cancel_pending_action') {
            pendingActionId = null;
            pendingBulkDecision = false;
            pendingCanBeVoiceResolved = false;
            pendingUserSpeechText = '';
            renderSummary('');
            setStatus('Cambio cancelado. No se aplicó ningún cambio.');
          }
          if (call.name === 'last_sale' && result.message) {
            suppressModelResponse = true;
            addMessage(result.message, 'assistant');
            speakFallback(result.message);
          } else if (result.message && !queryTools.has(call.name)) {
            addMessage(result.message, 'assistant');
          }
          response = {
            id: call.id,
            name: call.name,
            response: {
              result: result.actionId
                ? `Acción pendiente de confirmación del usuario: ${result.actionSummary}. No digas que ya se ejecutó.`
                : result.message
            }
          };
        } catch (error) {
          addMessage(error.message, 'error');
          response = {
            id: call.id,
            name: call.name,
            response: { error: error.message }
          };
        }
        functionResponses.push(response);
      }
      if (socket?.readyState === WebSocket.OPEN && functionResponses.length) {
        socket.send(JSON.stringify({ toolResponse: { functionResponses } }));
      }
    }

    function disconnectLive() {
      const currentSocket = socket;
      socket = null;
      connectionAttemptInProgress = false;
      window.clearTimeout(connectionTimeout);
      connectionTimeout = null;
      connectionReady = false;
      activationRequested = false;
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      window.clearTimeout(greetingTimeout);
      greetingTimeout = null;
      greetingStarted = false;
      greetingPending = false;
      greetingAudioReceived = false;
      greetingFallbackUsed = false;
      suppressModelResponse = false;
      currentModelTurn = createModelTurn();
      if (fallbackSpeaking) window.speechSynthesis?.cancel();
      fallbackSpeaking = false;
      updateVisualizer();
      stopMicrophone();
      stopOutputAudio();
      if (audioContext && audioContext.state !== 'closed') {
        audioContext.close();
        audioContext = null;
      }
      if (currentSocket && currentSocket.readyState < WebSocket.CLOSING) currentSocket.close();
      setBusy(false);
    }

    async function confirmAction() {
      if (!pendingActionId) return;
      setBusy(true, 'Aplicando cambio confirmado…');
      try {
        const response = await fetch(`${window.API_BASE || ''}/api/voice-assistant/confirm`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ actionId: pendingActionId })
        });
        const result = await response.json();
        if (!response.ok || !result.ok) {
          throw new Error(result.error || 'No se pudo aplicar el cambio.');
        }
        pendingActionId = null;
        pendingCanBeVoiceResolved = false;
        pendingUserSpeechText = '';
        renderSummary('');
        window.showToast('Cambio aplicado correctamente', 'success');
        try {
          await window.cargarProductos?.();
        } catch (error) {
          addMessage(`El cambio se aplicó, pero no se pudo actualizar el catálogo: ${error.message}`, 'error');
        }
        if (socket?.readyState === WebSocket.OPEN) {
          stopMicrophone();
          resumeMicrophoneAfterTurn = true;
          socket.send(JSON.stringify({
            realtimeInput: { text: `El usuario confirmó el cambio. ${result.message} Continúa la conversación sin cerrarla.` }
          }));
          setBusy(false, 'Cambio aplicado. El asistente sigue disponible.');
        } else {
          setBusy(false, 'Cambio aplicado. Puedes seguir consultando el inventario.');
        }
      } catch (error) {
        setBusy(false, 'No se pudo aplicar el cambio');
        addMessage(error.message, 'error');
      }
    }

    async function cancelAction() {
      if (!pendingActionId) return;
      setBusy(true, 'Cancelando cambio…');
      try {
        const response = await fetch(`${window.API_BASE || ''}/api/voice-assistant/cancel`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ actionId: pendingActionId })
        });
        const result = await response.json();
        if (!response.ok || !result.ok) {
          throw new Error(result.error || 'No se pudo cancelar el cambio.');
        }
        pendingActionId = null;
        pendingCanBeVoiceResolved = false;
        pendingUserSpeechText = '';
        renderSummary('');
        addMessage(result.message, 'assistant');
        if (socket?.readyState === WebSocket.OPEN) {
          resumeMicrophoneAfterTurn = true;
          socket.send(JSON.stringify({
            realtimeInput: { text: 'El usuario canceló el cambio propuesto. Confirma la cancelación y sigue escuchando.' }
          }));
          setBusy(false, 'Cambio cancelado. El asistente responderá y retomará la escucha.');
        } else {
          setBusy(false, 'Cambio cancelado. Puedes iniciar otra conversación.');
        }
      } catch (error) {
        setBusy(false, 'No se pudo cancelar el cambio');
        addMessage(error.message, 'error');
      }
    }

    function toggleMicrophone() {
      if (processing) return;
      if (live) {
        micMuted = !micMuted;
        updateMuteControl();
        setBusy(false, micMuted
          ? 'Micrófono silenciado; el asistente continúa activo.'
          : 'Micrófono activo; puedes hablar.');
      } else if (socket?.readyState === WebSocket.OPEN) {
        setBusy(true, 'Activando micrófono…');
        startMicrophone().catch(error => {
          setBusy(false);
          addMessage(error.message, 'error');
        });
      } else {
        activationRequested = true;
        connectLive(true);
      }
    }
    muteButton.addEventListener('click', toggleMicrophone);
    bubbleMuteButton.addEventListener('click', toggleMicrophone);
    newProductButton.addEventListener('click', event => {
      event.preventDefault();
      event.stopImmediatePropagation();
      window.resetModalProducto?.();
      window.nextStep?.(1);
      modal.style.display = 'flex';
      returnToProductForm = true;
      openAssistant();
    }, true);
    standaloneAssistantButton.addEventListener('click', openAssistant);
    assistantReturnButton.addEventListener('click', () => {
      returnToProductForm = true;
      openAssistant();
    });
    backButton.addEventListener('click', () => {
      showManualForm();
    });
    minimizeAssistantButton.addEventListener('click', minimizeAssistant);
    compactAssistantButton.addEventListener('click', toggleCompactAssistant);
    assistantBrand.addEventListener('click', () => {
      if (panel.classList.contains('is-compact')) expandAssistant();
    });
    closeAssistantButton.addEventListener('click', closeAssistant);
    expandAssistantButton.addEventListener('click', expandAssistant);
    restoreAssistantButton.addEventListener('click', expandAssistant);
    closeBubbleButton.addEventListener('click', closeAssistant);
    assistantHeader.addEventListener('pointerdown', movePanel);
    resizeHandles.forEach(handle => handle.addEventListener('pointerdown', resizePanel));
    confirmButton.addEventListener('click', confirmAction);
    cancelActionButton.addEventListener('click', cancelAction);
    updateMuteControl();
    renderSummary('');
    keepConnectionWarm = true;
    loadCatalogs().catch(error => {
      console.warn('No se pudieron precargar los catálogos del asistente:', error);
    });
    warmAssistantConnection().catch(error => {
      if (!panel.hidden) {
        setStatus(`No se pudo preparar el canal de voz: ${error.message}`, true);
      }
      scheduleWarmReconnect();
    });
  });
})();
