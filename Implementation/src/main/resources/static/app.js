(function () {
    const statusBadge = document.getElementById("statusBadge");
    const chatStream = document.getElementById("chatStream");
    const askForm = document.getElementById("askForm");
    const questionInput = document.getElementById("questionInput");
    const clearChatBtn = document.getElementById("clearChatBtn");
    const charCount = document.getElementById("charCount");
    const jumpLatestBtn = document.getElementById("jumpLatestBtn");
    const refreshAuditBtn = document.getElementById("refreshAuditBtn");
    const auditList = document.getElementById("auditList");
    const auditFilterInput = document.getElementById("auditFilterInput");
    const docUploadForm = document.getElementById("docUploadForm");
    const docInput = document.getElementById("docInput");
    const docsList = document.getElementById("docsList");
    const refreshDocsBtn = document.getElementById("refreshDocsBtn");
    const exampleForm = document.getElementById("exampleForm");
    const exampleQuestion = document.getElementById("exampleQuestion");
    const exampleSql = document.getElementById("exampleSql");
    const cacheRefreshBtn = document.getElementById("cacheRefreshBtn");
    const cacheClearBtn = document.getElementById("cacheClearBtn");
    const suggestionChips = document.getElementById("suggestionChips");
    const savedPromptChips = document.getElementById("savedPromptChips");
    const refreshHistoryBtn = document.getElementById("refreshHistoryBtn");
    const historyList = document.getElementById("historyList");
    const historyFilterInput = document.getElementById("historyFilterInput");
    const totalQuestionsStat = document.getElementById("totalQuestionsStat");
    const cacheHitRateStat = document.getElementById("cacheHitRateStat");
    const avgLatencyStat = document.getElementById("avgLatencyStat");
    const toastContainer = document.getElementById("toastContainer");
    const askButton = askForm.querySelector("button[type='submit']");
    const sideTabs = Array.from(document.querySelectorAll(".side-tab"));
    const panes = Array.from(document.querySelectorAll(".pane"));

    const userId = "user-" + Math.random().toString(36).slice(2, 8);
    const savedPromptsKey = "genai-cbi-saved-prompts";
    const feedbackStateKey = "genai-cbi-feedback-state";
    const chartInstances = new WeakMap();
    let chartLibraryWarningShown = false;

    let historyCache = [];
    let auditCache = [];
    let feedbackStateByCacheKey = loadFeedbackState();

    askForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const question = questionInput.value.trim();
        if (!question) {
            return;
        }
        questionInput.value = "";
        syncComposerState();
        await askQuestion(question);
    });

    questionInput.addEventListener("keydown", async (event) => {
        if (event.ctrlKey && event.key === "Enter") {
            event.preventDefault();
            const question = questionInput.value.trim();
            if (!question) {
                return;
            }
            questionInput.value = "";
            syncComposerState();
            await askQuestion(question);
        }
    });

    questionInput.addEventListener("input", syncComposerState);

    document.addEventListener("keydown", (event) => {
        if (event.key === "/" && !isTypingContext(event.target)) {
            event.preventDefault();
            questionInput.focus();
        }
    });

    clearChatBtn.addEventListener("click", () => {
        chatStream.innerHTML = "";
        appendTextBubble("assistant", "Chat cleared. Ask a new BI question.", "Assistant");
    });

    jumpLatestBtn.addEventListener("click", () => {
        chatStream.scrollTop = chatStream.scrollHeight;
    });

    chatStream.addEventListener("scroll", () => {
        const hiddenBottom = chatStream.scrollHeight - chatStream.clientHeight - chatStream.scrollTop;
        jumpLatestBtn.style.visibility = hiddenBottom > 180 ? "visible" : "hidden";
    });

    sideTabs.forEach((tab) => {
        tab.addEventListener("click", () => {
            const target = tab.dataset.tab;
            sideTabs.forEach((button) => button.classList.toggle("active", button === tab));
            panes.forEach((pane) => pane.classList.toggle("active", pane.dataset.pane === target));
        });
    });

    historyFilterInput.addEventListener("input", renderHistoryFromFilter);
    auditFilterInput.addEventListener("input", renderAuditFromFilter);

    docUploadForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!docInput.files.length) {
            return;
        }

        const form = new FormData();
        form.append("file", docInput.files[0]);
        form.append("userId", userId);

        try {
            await fetchJson("/api/training/documents", {
                method: "POST",
                body: form
            });
            docInput.value = "";
            await loadDocuments();
            setStatus("Training document uploaded");
            showToast("Training document uploaded", "success");
        } catch (error) {
            setStatus("Upload failed");
            showToast(error.message, "error");
        }
    });

    exampleForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const question = exampleQuestion.value.trim();
        const sql = exampleSql.value.trim();
        if (!question || !sql) {
            return;
        }
        try {
            await fetchJson("/api/training/examples", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({question, sql})
            });
            exampleQuestion.value = "";
            exampleSql.value = "";
            setStatus("Training example added");
            await loadSuggestions();
            showToast("Training example added", "success");
        } catch (error) {
            setStatus("Failed to add example");
            showToast(error.message, "error");
        }
    });

    refreshDocsBtn.addEventListener("click", loadDocuments);
    refreshAuditBtn.addEventListener("click", loadAudit);
    refreshHistoryBtn.addEventListener("click", loadHistory);

    cacheRefreshBtn.addEventListener("click", async () => {
        try {
            const result = await fetchJson("/api/admin/cache/refresh", {method: "POST"});
            setStatus("Cache refresh removed: " + result.removed);
            showToast(`Purged stale cache entries: ${result.removed}`, "info");
        } catch (error) {
            setStatus("Cache refresh failed");
            showToast("Cache refresh failed", "error");
        }
    });

    cacheClearBtn.addEventListener("click", async () => {
        if (!confirm("Clear all cache and vector entries?")) {
            return;
        }
        try {
            const result = await fetchJson("/api/admin/cache/clear", {method: "POST"});
            setStatus("Cache cleared: " + result.cacheRemoved);
            showToast(`Cache cleared: ${result.cacheRemoved}`, "info");
        } catch (error) {
            setStatus("Cache clear failed");
            showToast("Cache clear failed", "error");
        }
    });

    async function askQuestion(question) {
        appendTextBubble("user", question, "You");
        setStatus("Thinking...");
        setAskEnabled(false);
        const loadingNode = appendLoadingBubble();

        try {
            const answer = await fetchJson("/api/chat/ask", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({question, userId})
            });
            loadingNode.remove();
            appendAssistantBubble(answer, question);
            await Promise.all([loadHistory(), loadStats()]);
            setStatus("Ready");
            showToast(`Answer ready (${answer.source})`, "success");
        } catch (error) {
            loadingNode.remove();
            appendTextBubble("assistant", "Error: " + error.message, "Assistant");
            setStatus("Error");
            showToast(error.message, "error");
        } finally {
            setAskEnabled(true);
        }
    }

    function setAskEnabled(enabled) {
        askButton.disabled = !enabled;
        askButton.style.opacity = enabled ? "1" : "0.6";
    }

    function syncComposerState() {
        const text = questionInput.value;
        charCount.textContent = `${text.length} chars`;
        questionInput.style.height = "auto";
        questionInput.style.height = `${Math.min(220, questionInput.scrollHeight)}px`;
    }

    function appendTextBubble(role, text, label) {
        const bubble = document.createElement("div");
        bubble.className = `bubble ${role}`;

        const header = document.createElement("div");
        header.className = "bubble-header";
        header.innerHTML = `<strong>${label}</strong><span class="meta">${new Date().toLocaleTimeString()}</span>`;

        const body = document.createElement("div");
        body.textContent = text;

        bubble.appendChild(header);
        bubble.appendChild(body);
        chatStream.appendChild(bubble);
        chatStream.scrollTop = chatStream.scrollHeight;
    }

    function appendLoadingBubble() {
        const bubble = document.createElement("div");
        bubble.className = "bubble assistant";
        const row = document.createElement("div");
        row.className = "loading-row";
        row.innerHTML = `<div class="spinner"></div><span>Generating SQL, executing query, and preparing chart...</span>`;
        bubble.appendChild(row);
        chatStream.appendChild(bubble);
        chatStream.scrollTop = chatStream.scrollHeight;
        return bubble;
    }

    function appendAssistantBubble(answer, originalQuestion) {
        const bubble = document.createElement("div");
        bubble.className = "bubble assistant";

        const header = document.createElement("div");
        header.className = "bubble-header";
        header.innerHTML = `
            <strong>Assistant</strong>
            <span class="meta">${answer.source} | ${answer.fromCache ? "cache" : "generated"}</span>
        `;
        bubble.appendChild(header);

        const meta = document.createElement("div");
        meta.className = "assistant-meta";
        meta.appendChild(buildPill(`Rows: ${answer.rowCount}`));
        meta.appendChild(buildPill(`Latency: ${answer.latencyMs} ms`));
        meta.appendChild(buildPill(`Confidence: ${(answer.confidenceScore * 100).toFixed(0)}%`));
        bubble.appendChild(meta);

        const summary = document.createElement("div");
        summary.textContent = answer.summary;
        bubble.appendChild(summary);

        const sql = document.createElement("pre");
        sql.className = "sql-block";
        sql.textContent = answer.sql;
        bubble.appendChild(sql);

        let currentChartType = answer.chart?.type === "line" ? "line" : "bar";
        let canvas = null;
        if (answer.chart && answer.chart.labels && answer.chart.labels.length) {
            const chartWrap = document.createElement("div");
            chartWrap.className = "chart-wrap";
            canvas = document.createElement("canvas");
            chartWrap.appendChild(canvas);
            bubble.appendChild(chartWrap);
            renderChart(canvas, answer.chart.labels, answer.chart.values || [], currentChartType, answer.chart.title || "Result");
        }

        if (answer.rows && answer.rows.length) {
            bubble.appendChild(buildTable(answer.columns, answer.rows));
        }

        if (answer.followUpSuggestions && answer.followUpSuggestions.length) {
            const followUps = document.createElement("div");
            followUps.className = "follow-ups";
            const strong = document.createElement("strong");
            strong.textContent = "Follow-up ideas";
            followUps.appendChild(strong);
            answer.followUpSuggestions.forEach((item) => {
                const row = document.createElement("div");
                row.style.marginTop = "0.35rem";
                const button = document.createElement("button");
                button.className = "ghost";
                button.style.fontSize = "0.75rem";
                button.textContent = item;
                button.addEventListener("click", () => askQuestion(item));
                row.appendChild(button);
                followUps.appendChild(row);
            });
            bubble.appendChild(followUps);
        }

        if (answer.reasoningTrail && answer.reasoningTrail.length) {
            const trail = document.createElement("details");
            trail.className = "trail";
            trail.innerHTML = `<summary>How this answer was produced</summary>`;
            const list = document.createElement("ol");
            list.className = "trail-list";
            answer.reasoningTrail.forEach((item) => {
                const li = document.createElement("li");
                li.textContent = item;
                list.appendChild(li);
            });
            trail.appendChild(list);
            bubble.appendChild(trail);
        }

        const feedbackState = document.createElement("div");
        feedbackState.className = "feedback-state";
        bubble.appendChild(feedbackState);

        const feedbackRow = document.createElement("div");
        feedbackRow.className = "feedback-row";

        const approveBtn = document.createElement("button");
        approveBtn.className = "ghost";
        approveBtn.textContent = "Approve";

        const disapproveBtn = document.createElement("button");
        disapproveBtn.className = "danger";
        disapproveBtn.textContent = "Disapprove";

        const copySqlBtn = document.createElement("button");
        copySqlBtn.className = "secondary";
        copySqlBtn.textContent = "Copy SQL";
        copySqlBtn.addEventListener("click", async () => {
            await navigator.clipboard.writeText(answer.sql || "");
            showToast("SQL copied to clipboard", "info");
        });

        const exportBtn = document.createElement("button");
        exportBtn.className = "ghost";
        exportBtn.textContent = "Export CSV";
        exportBtn.addEventListener("click", () => exportCsv(answer.cacheKey));

        const savePromptBtn = document.createElement("button");
        savePromptBtn.className = "ghost";
        savePromptBtn.textContent = "Save Prompt";
        savePromptBtn.addEventListener("click", () => {
            savePrompt(originalQuestion);
            showToast("Prompt saved", "success");
        });

        if (canvas && answer.chart?.labels?.length) {
            const toggleChartBtn = document.createElement("button");
            toggleChartBtn.className = "ghost";
            toggleChartBtn.textContent = currentChartType === "line" ? "Chart: Line" : "Chart: Bar";
            toggleChartBtn.addEventListener("click", () => {
                currentChartType = currentChartType === "bar" ? "line" : "bar";
                toggleChartBtn.textContent = currentChartType === "line" ? "Chart: Line" : "Chart: Bar";
                renderChart(canvas, answer.chart.labels, answer.chart.values || [], currentChartType, answer.chart.title || "Result");
            });
            feedbackRow.appendChild(toggleChartBtn);
        }

        function renderFeedbackUi(vote, pending) {
            approveBtn.classList.toggle("active-approve", vote === "APPROVE");
            disapproveBtn.classList.toggle("active-disapprove", vote === "DISAPPROVE");
            approveBtn.disabled = pending;
            disapproveBtn.disabled = pending;

            if (pending) {
                feedbackState.className = "feedback-state";
                feedbackState.textContent = "Feedback: saving...";
                return;
            }

            if (vote === "APPROVE") {
                feedbackState.className = "feedback-state approve";
                feedbackState.textContent = "Feedback: approved";
            } else if (vote === "DISAPPROVE") {
                feedbackState.className = "feedback-state disapprove";
                feedbackState.textContent = "Feedback: disapproved";
            } else {
                feedbackState.className = "feedback-state";
                feedbackState.textContent = "Feedback: not rated yet";
            }
        }

        const initialVote = feedbackStateByCacheKey[answer.cacheKey]?.vote;
        renderFeedbackUi(initialVote, false);

        approveBtn.addEventListener("click", async () => {
            await submitFeedback(answer.cacheKey, "APPROVE", renderFeedbackUi);
        });
        disapproveBtn.addEventListener("click", async () => {
            await submitFeedback(answer.cacheKey, "DISAPPROVE", renderFeedbackUi);
        });

        feedbackRow.appendChild(approveBtn);
        feedbackRow.appendChild(disapproveBtn);
        feedbackRow.appendChild(copySqlBtn);
        feedbackRow.appendChild(exportBtn);
        feedbackRow.appendChild(savePromptBtn);
        bubble.appendChild(feedbackRow);

        chatStream.appendChild(bubble);
        chatStream.scrollTop = chatStream.scrollHeight;
    }

    function buildPill(text) {
        const pill = document.createElement("span");
        pill.className = "pill";
        pill.textContent = text;
        return pill;
    }

    async function submitFeedback(cacheKey, vote, renderer) {
        const currentVote = feedbackStateByCacheKey[cacheKey]?.vote;
        renderer(currentVote, true);
        try {
            await fetchJson("/api/feedback", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({cacheKey, vote, userId})
            });
            feedbackStateByCacheKey[cacheKey] = {vote, timestamp: new Date().toISOString()};
            persistFeedbackState(feedbackStateByCacheKey);
            renderer(vote, false);
            setStatus("Feedback recorded: " + vote);
            showToast(`Feedback recorded: ${vote}`, "success");
        } catch (error) {
            renderer(currentVote, false);
            setStatus("Feedback failed");
            showToast("Feedback failed", "error");
        }
    }

    function buildTable(columns, rows) {
        const wrap = document.createElement("div");
        wrap.className = "table-wrap";
        const table = document.createElement("table");
        const thead = document.createElement("thead");
        const tr = document.createElement("tr");
        columns.forEach((col) => {
            const th = document.createElement("th");
            th.textContent = col;
            tr.appendChild(th);
        });
        thead.appendChild(tr);
        table.appendChild(thead);
        const tbody = document.createElement("tbody");

        rows.slice(0, 20).forEach((row) => {
            const bodyRow = document.createElement("tr");
            columns.forEach((col) => {
                const td = document.createElement("td");
                const value = row[col];
                td.textContent = value === null || value === undefined ? "" : String(value);
                bodyRow.appendChild(td);
            });
            tbody.appendChild(bodyRow);
        });
        table.appendChild(tbody);
        wrap.appendChild(table);
        return wrap;
    }

    function renderChart(canvas, labels, values, type, title) {
        const safeLabels = Array.isArray(labels) ? labels.map((label) => String(label)) : [];
        const safeValues = Array.isArray(values) ? values.map((v) => Number(v) || 0) : [];

        if (typeof Chart !== "undefined") {
            renderChartJs(canvas, safeLabels, safeValues, type, title);
            return;
        }
        if (!chartLibraryWarningShown) {
            chartLibraryWarningShown = true;
            showToast("Chart.js unavailable. Falling back to basic chart rendering.", "info");
        }
        renderBasicCanvasChart(canvas, safeLabels, safeValues, type);
    }

    function renderChartJs(canvas, labels, values, type, title) {
        const existing = chartInstances.get(canvas);
        if (existing) {
            existing.destroy();
        }

        const chart = new Chart(canvas, {
            type: type === "line" ? "line" : "bar",
            data: {
                labels,
                datasets: [
                    {
                        label: title || "Metric",
                        data: values,
                        backgroundColor: type === "line"
                                ? "rgba(37, 123, 218, 0.22)"
                                : "rgba(11, 122, 117, 0.72)",
                        borderColor: type === "line" ? "#257bda" : "#0b7a75",
                        borderWidth: type === "line" ? 2 : 1,
                        borderRadius: type === "line" ? 0 : 8,
                        tension: type === "line" ? 0.35 : 0,
                        fill: type === "line",
                        pointRadius: type === "line" ? 3 : 0,
                        pointHoverRadius: type === "line" ? 5 : 0,
                        maxBarThickness: 42
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: "nearest",
                    intersect: false
                },
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: "rgba(17, 33, 51, 0.95)",
                        titleColor: "#ffffff",
                        bodyColor: "#eef4ff",
                        padding: 10,
                        cornerRadius: 8,
                        callbacks: {
                            label: (ctx) => `${ctx.dataset.label}: ${formatNumber(ctx.raw)}`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: {
                            color: "rgba(152, 173, 194, 0.18)",
                            drawBorder: false
                        },
                        ticks: {
                            color: "#4f6378",
                            maxRotation: 0,
                            autoSkip: true,
                            maxTicksLimit: 8,
                            callback: (value, index) => trimLabel(labels[index], 12)
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: "rgba(152, 173, 194, 0.18)",
                            drawBorder: false
                        },
                        ticks: {
                            color: "#4f6378",
                            callback: (value) => compactNumber(value)
                        }
                    }
                }
            }
        });

        chartInstances.set(canvas, chart);
    }

    function renderBasicCanvasChart(canvas, labels, values, type) {
        if (type === "line") {
            drawLineChart(canvas, labels, values);
            return;
        }
        drawBarChart(canvas, labels, values);
    }

    function drawBarChart(canvas, labels, values) {
        const ctx = canvas.getContext("2d");
        const width = canvas.width || 560;
        const height = canvas.height || 260;
        const max = Math.max(...values, 1);
        const padding = 32;
        const graphHeight = height - padding * 2;
        const barWidth = Math.max(12, (width - padding * 2) / Math.max(1, values.length) - 10);

        ctx.clearRect(0, 0, width, height);
        ctx.fillStyle = "#f3f9fd";
        ctx.fillRect(0, 0, width, height);

        ctx.strokeStyle = "#9fb7cc";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(padding, padding);
        ctx.lineTo(padding, height - padding);
        ctx.lineTo(width - padding, height - padding);
        ctx.stroke();

        values.forEach((value, index) => {
            const x = padding + index * (barWidth + 10) + 6;
            const barHeight = (value / max) * graphHeight;
            const y = height - padding - barHeight;
            ctx.fillStyle = "#0b7a75";
            ctx.fillRect(x, y, barWidth, barHeight);

            ctx.fillStyle = "#355269";
            ctx.font = "11px Segoe UI";
            ctx.textAlign = "center";
            ctx.fillText(trimLabel(labels[index], 8), x + barWidth / 2, height - padding + 13);
        });
    }

    function drawLineChart(canvas, labels, values) {
        const ctx = canvas.getContext("2d");
        const width = canvas.width || 560;
        const height = canvas.height || 260;
        const padding = 30;
        const max = Math.max(...values, 1);
        const min = Math.min(...values, 0);
        const range = Math.max(1, max - min);

        ctx.clearRect(0, 0, width, height);
        ctx.fillStyle = "#f4f8fc";
        ctx.fillRect(0, 0, width, height);

        ctx.strokeStyle = "#9fb7cc";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(padding, padding);
        ctx.lineTo(padding, height - padding);
        ctx.lineTo(width - padding, height - padding);
        ctx.stroke();

        const step = values.length > 1 ? (width - 2 * padding) / (values.length - 1) : 0;
        ctx.strokeStyle = "#257bda";
        ctx.lineWidth = 2;
        ctx.beginPath();
        values.forEach((value, index) => {
            const x = padding + index * step;
            const y = height - padding - ((value - min) / range) * (height - 2 * padding);
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();

        values.forEach((value, index) => {
            const x = padding + index * step;
            const y = height - padding - ((value - min) / range) * (height - 2 * padding);
            ctx.fillStyle = "#257bda";
            ctx.beginPath();
            ctx.arc(x, y, 3.2, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = "#355269";
            ctx.font = "10px Segoe UI";
            ctx.textAlign = "center";
            ctx.fillText(trimLabel(labels[index], 8), x, height - padding + 12);
        });
    }

    async function loadDocuments() {
        try {
            const docs = await fetchJson("/api/training/documents");
            docsList.innerHTML = "";
            if (!docs.length) {
                docsList.textContent = "No documents uploaded yet.";
                return;
            }
            docs.forEach((doc) => {
                const item = document.createElement("div");
                item.className = "list-item";
                item.textContent = `${doc.fileName} | ${new Date(doc.uploadedAt).toLocaleString()}`;
                docsList.appendChild(item);
            });
        } catch (error) {
            docsList.textContent = "Unable to load documents.";
        }
    }

    async function loadAudit() {
        try {
            auditCache = await fetchJson("/api/audit?limit=30");
            renderAuditFromFilter();
        } catch (error) {
            auditList.textContent = "Unable to load audit events.";
        }
    }

    function renderAuditFromFilter() {
        const query = (auditFilterInput.value || "").toLowerCase();
        const filtered = auditCache.filter((item) => {
            return `${item.timestamp} ${item.action} ${item.status} ${item.message}`.toLowerCase().includes(query);
        });

        auditList.innerHTML = "";
        if (!filtered.length) {
            auditList.textContent = "No matching audit events.";
            return;
        }
        filtered.forEach((event) => {
            const item = document.createElement("div");
            item.className = "list-item";
            item.textContent = `${event.timestamp} | ${event.action} | ${event.status} (${event.latencyMs} ms)`;
            auditList.appendChild(item);
        });
    }

    async function loadHistory() {
        try {
            historyCache = await fetchJson(`/api/chat/history?userId=${encodeURIComponent(userId)}&limit=20`);
            renderHistoryFromFilter();
        } catch (error) {
            historyList.textContent = "Unable to load history.";
        }
    }

    function renderHistoryFromFilter() {
        const query = (historyFilterInput.value || "").toLowerCase();
        const filtered = historyCache.filter((item) => {
            return `${item.question} ${item.source} ${item.summary}`.toLowerCase().includes(query);
        });

        historyList.innerHTML = "";
        if (!filtered.length) {
            historyList.textContent = "No matching history.";
            return;
        }
        filtered.forEach((item) => {
            const block = document.createElement("div");
            block.className = "list-item";

            const q = document.createElement("div");
            q.className = "history-question";
            q.textContent = item.question;

            const meta = document.createElement("div");
            meta.className = "history-meta";
            meta.textContent = `${item.source} | ${item.rowCount} rows | ${item.latencyMs} ms`;

            const row = document.createElement("div");
            row.className = "button-row";
            row.style.marginTop = "0.35rem";

            const reaskBtn = document.createElement("button");
            reaskBtn.className = "ghost small";
            reaskBtn.textContent = "Ask Again";
            reaskBtn.addEventListener("click", () => askQuestion(item.question));

            const saveBtn = document.createElement("button");
            saveBtn.className = "secondary small";
            saveBtn.textContent = "Save";
            saveBtn.addEventListener("click", () => {
                savePrompt(item.question);
                showToast("Prompt saved", "success");
            });

            row.appendChild(reaskBtn);
            row.appendChild(saveBtn);
            block.appendChild(q);
            block.appendChild(meta);
            block.appendChild(row);
            historyList.appendChild(block);
        });
    }

    async function loadStats() {
        try {
            const stats = await fetchJson(`/api/chat/stats?userId=${encodeURIComponent(userId)}`);
            totalQuestionsStat.textContent = String(stats.totalQuestions);
            cacheHitRateStat.textContent = `${Number(stats.cacheHitRate).toFixed(1)}%`;
            avgLatencyStat.textContent = `${Math.round(Number(stats.averageLatencyMs))} ms`;
        } catch (error) {
            totalQuestionsStat.textContent = "0";
            cacheHitRateStat.textContent = "0%";
            avgLatencyStat.textContent = "0 ms";
        }
    }

    async function loadSuggestions() {
        try {
            const suggestions = await fetchJson("/api/chat/suggestions?limit=8");
            suggestionChips.innerHTML = "";
            suggestions.forEach((suggestion) => {
                const chip = document.createElement("button");
                chip.className = "chip";
                chip.type = "button";
                chip.textContent = suggestion;
                chip.addEventListener("click", () => askQuestion(suggestion));
                suggestionChips.appendChild(chip);
            });
        } catch (error) {
            suggestionChips.innerHTML = "";
        }
    }

    function loadSavedPrompts() {
        try {
            const raw = localStorage.getItem(savedPromptsKey);
            if (!raw) {
                return [];
            }
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                return [];
            }
            return parsed.filter((item) => typeof item === "string");
        } catch (error) {
            return [];
        }
    }

    function savePrompt(prompt) {
        const normalized = String(prompt || "").trim();
        if (!normalized) {
            return;
        }
        const existing = loadSavedPrompts();
        const merged = [normalized, ...existing.filter((item) => item !== normalized)].slice(0, 12);
        localStorage.setItem(savedPromptsKey, JSON.stringify(merged));
        renderSavedPromptChips();
    }

    function removePrompt(prompt) {
        const updated = loadSavedPrompts().filter((item) => item !== prompt);
        localStorage.setItem(savedPromptsKey, JSON.stringify(updated));
        renderSavedPromptChips();
    }

    function renderSavedPromptChips() {
        const prompts = loadSavedPrompts();
        savedPromptChips.innerHTML = "";
        if (!prompts.length) {
            const note = document.createElement("div");
            note.className = "empty-note";
            note.textContent = "No saved prompts yet. Save from any answer card.";
            savedPromptChips.appendChild(note);
            return;
        }
        prompts.forEach((prompt) => {
            const wrapper = document.createElement("div");
            wrapper.style.display = "flex";
            wrapper.style.alignItems = "center";
            wrapper.style.gap = "0.3rem";

            const chip = document.createElement("button");
            chip.className = "chip saved";
            chip.type = "button";
            chip.textContent = prompt;
            chip.addEventListener("click", () => askQuestion(prompt));

            const remove = document.createElement("button");
            remove.className = "danger small";
            remove.type = "button";
            remove.textContent = "x";
            remove.addEventListener("click", () => removePrompt(prompt));

            wrapper.appendChild(chip);
            wrapper.appendChild(remove);
            savedPromptChips.appendChild(wrapper);
        });
    }

    async function exportCsv(cacheKey) {
        try {
            const response = await fetch(`/api/chat/export/${encodeURIComponent(cacheKey)}`);
            if (!response.ok) {
                throw new Error(`Export failed: ${response.status}`);
            }
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `answer-${cacheKey}.csv`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
            showToast("CSV export downloaded", "success");
        } catch (error) {
            showToast(error.message, "error");
        }
    }

    function loadFeedbackState() {
        try {
            const raw = localStorage.getItem(feedbackStateKey);
            if (!raw) {
                return {};
            }
            const parsed = JSON.parse(raw);
            return parsed && typeof parsed === "object" ? parsed : {};
        } catch (error) {
            return {};
        }
    }

    function persistFeedbackState(state) {
        localStorage.setItem(feedbackStateKey, JSON.stringify(state));
    }

    function trimLabel(label, maxLength) {
        const text = String(label || "");
        return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
    }

    function compactNumber(value) {
        const n = Number(value) || 0;
        if (Math.abs(n) >= 1000000) {
            return `${(n / 1000000).toFixed(1)}M`;
        }
        if (Math.abs(n) >= 1000) {
            return `${(n / 1000).toFixed(1)}K`;
        }
        return String(Math.round(n * 100) / 100);
    }

    function formatNumber(value) {
        const n = Number(value) || 0;
        return n.toLocaleString(undefined, {maximumFractionDigits: 2});
    }

    function showToast(message, type) {
        const toast = document.createElement("div");
        toast.className = `toast ${type || "info"}`;
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(() => toast.remove(), 2500);
    }

    function setStatus(text) {
        statusBadge.textContent = text;
    }

    function isTypingContext(target) {
        if (!target) {
            return false;
        }
        const tag = target.tagName ? target.tagName.toLowerCase() : "";
        return tag === "input" || tag === "textarea" || target.isContentEditable;
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || body.error || `Request failed: ${response.status}`);
        }
        return response.json();
    }

    appendTextBubble("assistant", "Ask a BI question to start.", "Assistant");
    syncComposerState();
    renderSavedPromptChips();
    loadSuggestions();
    loadHistory();
    loadStats();
    loadDocuments();
    loadAudit();
})();
