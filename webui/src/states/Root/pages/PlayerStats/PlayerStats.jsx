import {useEffect, useMemo, useState} from "react";
import {Box, Stack, Typography, ToggleButtonGroup, ToggleButton, Paper} from "@mui/material";
import {LineChart} from "@mui/x-charts";
import {jsonRequest} from "@/common/utils/RequestUtil";
import {t} from "i18next";

// --- Presets d'intervalle ---
const PRESETS = [
  {id: "6h",  label: "24H".replace("24H","6H"),  ms: 6 * 60 * 60 * 1000},
  {id: "1d",  label: "1J",   ms: 24 * 60 * 60 * 1000},
  {id: "3d",  label: "3J",   ms: 3 * 24 * 60 * 60 * 1000},
  {id: "1w",  label: "1S",   ms: 7 * 24 * 60 * 60 * 1000},
  {id: "2w",  label: "2S",   ms: 14 * 24 * 60 * 60 * 1000},
  {id: "1m",  label: "1M",   ms: 30 * 24 * 60 * 60 * 1000},
  {id: "2m",  label: "2M",   ms: 60 * 24 * 60 * 60 * 1000},
  {id: "3m",  label: "3M",   ms: 90 * 24 * 60 * 60 * 1000},
  {id: "6m",  label: "6M",   ms: 180 * 24 * 60 * 60 * 1000},
  {id: "1y",  label: "1A",   ms: 365 * 24 * 60 * 60 * 1000},
];

// bucket auto pour viser ~240 points max
const chooseBucketMinutes = (rangeMs) => {
  const targetPoints = 240; // lisible
  const minutes = Math.max(1, Math.round(rangeMs / (targetPoints * 60 * 1000)));
  return minutes;
};

const fmtTime = (rangeMs) => {
  // format de l’axe X adapté à la période
  if (rangeMs <= 24 * 60 * 60 * 1000) {
    return new Intl.DateTimeFormat(undefined, {hour: "2-digit", minute: "2-digit"});
  }
  if (rangeMs <= 14 * 24 * 60 * 60 * 1000) {
    return new Intl.DateTimeFormat(undefined, {weekday: "short", hour: "2-digit"});
  }
  if (rangeMs <= 90 * 24 * 60 * 60 * 1000) {
    return new Intl.DateTimeFormat(undefined, {day: "2-digit", month: "short"});
  }
  return new Intl.DateTimeFormat(undefined, {month: "short", year: "numeric"});
};

export default function PlayerStats() {
  const [preset, setPreset] = useState("7d"); // valeur fallback si tu veux, sinon mets "1w"
  // normalise : si "7d" n'existe pas dans PRESETS on prendra "1w" ci-dessous
  const active = PRESETS.find(p => p.id === preset) || PRESETS.find(p => p.id === "1w");
  const [loading, setLoading] = useState(false);
  const [points, setPoints] = useState([]); // [{tsUtc, players}]

  const fetchData = async () => {
    try {
      setLoading(true);
      const to = Date.now();
      const from = to - active.ms;
      const bucket = chooseBucketMinutes(active.ms);
      // backend accepte from/to ISO ou epoch
      const res = await jsonRequest(
        `stats/players/concurrency?from=${from}&to=${to}&bucket=${bucket}`
      );
      setPoints(Array.isArray(res) ? res : []);
    } catch (e) {
      console.error("fetch concurrency failed", e);
      setPoints([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const id = setInterval(fetchData, 60_000); // refresh 1 min
    return () => clearInterval(id);
  }, [preset]);

  const series = useMemo(() => {
    if (!Array.isArray(points) || points.length === 0) return { x: [], y: [] };
    const x = [];
    const y = [];
    for (const p of points) {
      const ms = typeof p.tsUtc === "number"
        ? (p.tsUtc < 2_000_000_000 ? p.tsUtc * 1000 : p.tsUtc)
        : Date.parse(p.tsUtc);
      if (Number.isFinite(ms)) {
        x.push(new Date(ms));
        y.push(Number(p.players ?? 0));
      }
    }
    return { x, y };
  }, [points]);

  const timeFmt = fmtTime(active.ms);

  return (
    <Stack gap={2} sx={{ mt: 1 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Typography variant="h5" fontWeight={500}>{t("nav.players_stats")}</Typography>

        <ToggleButtonGroup size="small" exclusive
          value={active.id}
          onChange={(_, v) => v && setPreset(v)}
        >
          {PRESETS.map(p => (
            <ToggleButton key={p.id} value={p.id}>{p.label}</ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Box sx={{ height: 420 }}>
          {series.x.length > 0 ? (
            <LineChart
              series={[{
                data: series.y,
                label: t("players_stats.concurrent_players"),
                showMark: series.x.length <= 120, // pas de points si trop dense
                area: true
              }]}
              xAxis={[{
                data: series.x,
                scaleType: "time",
                tickNumber: 8,                          // limite le nb de ticks
                valueFormatter: (d) => timeFmt.format(d),
              }]}
              yAxis={[{
                min: 0,
                tickMinStep: 1,                         // ticks entiers
              }]}
              tooltip={{ trigger: "axis" }}
              grid={{ vertical: true, horizontal: true }}
              loading={loading}
            />
          ) : (
            <Typography variant="body2" color="text.secondary">
              {loading ? "Chargement…" : "Aucune donnée pour la période sélectionnée."}
            </Typography>
          )}
        </Box>
      </Paper>
    </Stack>
  );
}
