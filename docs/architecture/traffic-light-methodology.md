# WattWise — Traffic Light Methodology

Detailed specification of the hybrid price classification system that assigns a color (green, amber, or red) to each electricity price slot.

---

## 1. Problem Statement

With 96 daily price slots (15-minute intervals since September 2025), users cannot intuitively identify cheap vs. expensive periods. A simple "below average = good" approach fails because:

- **Bimodal distributions** (common in Spain: cheap overnight, expensive evening) make the average meaningless as a single threshold.
- **Absolute price context matters:** A "cheap" hour at 0.08 EUR/kWh is still expensive in a market where 0.04 EUR/kWh exists.
- **User expectations shift:** A day where all prices are low (e.g., Sunday with excess renewables) should still differentiate between "best" and "OK."

WattWise uses a **hybrid approach** combining three classification layers: relative percentiles, deviation from the mean, and absolute context thresholds.

---

## 2. Classification Layers

### Layer 1: Daily Percentile Ranking

Each slot is ranked against all other slots **of the same day**.

**Formula:**

```
percentile(slot) = rank(slot) / total_slots_today
```

Where `rank` is the 0-indexed position when all slots are sorted by price ascending.

**Classification from percentiles alone:**

| Percentile | Color | Meaning |
|---|---|---|
| 0.00 – 0.33 | 🟢 GREEN | Bottom third — cheapest 33% of the day |
| 0.34 – 0.66 | 🟡 AMBER | Middle third — moderate prices |
| 0.67 – 1.00 | 🔴 RED | Top third — most expensive 33% of the day |

**Why this works:** Regardless of the absolute price level, the user always knows which third of the day is cheapest relative to that specific day. On a cheap Sunday, "green" still means "today's best," even if today's best is 0.06 EUR/kWh.

### Layer 2: Deviation from the Daily Mean

Measures how far each slot deviates from the arithmetic mean of the day.

**Formula:**

```
deviation(slot) = (price(slot) - mean_price_today) / mean_price_today
```

This gives a percentage deviation (e.g., -0.40 means 40% below average).

**Classification from deviation:**

| Deviation | Color | Meaning |
|---|---|---|
| < -0.25 (-25%) | 🟢 GREEN | Significantly below average |
| -0.25 to +0.25 | 🟡 AMBER | Near average (±25%) |
| > +0.25 (+25%) | 🔴 RED | Significantly above average |

**Why this matters:** In days with a flat price curve (e.g., a windy Sunday where all 96 slots are 0.04–0.06 EUR/kWh), percentiles would still force a green/amber/red split even though the practical difference is negligible. The deviation layer detects this: if all slots are within ±25% of the mean, the system recognizes the day as "uniformly priced."

### Layer 3: Absolute Context Thresholds

Applies fixed price thresholds derived from the Spanish PVPC historical context.

**Thresholds (calibrated to 2024-2025 PVPC data):**

| Price Range (EUR/kWh) | Color | Context |
|---|---|---|
| < 0.05 | 🟢 GREEN | Very cheap — historical off-peak floor |
| 0.05 – 0.12 | 🟡 AMBER | Normal — typical mid-range |
| 0.12 – 0.20 | 🟠 AMBER (caution) | Above average — consider delaying |
| > 0.20 | 🔴 RED | Expensive — peak pricing, avoid if possible |

> **Note:** These thresholds are **configurable** in `application.yml` and will be adjusted as market conditions evolve. The values above represent the 2024-2025 Spanish PVPC context.

**Why this matters:** Without absolute thresholds, a day where all prices are 0.15–0.25 EUR/kWh (a genuinely expensive day) would still show 32 "green" slots — misleading the user into thinking some options are "cheap" when none are truly affordable.

---

## 3. Hybrid Fusion Algorithm

The final color is determined by **voting across all three layers** with priority rules.

### Algorithm Steps

```
INPUT:  price_slot (96 values for the day)
OUTPUT: traffic_light per slot (GREEN | AMBER | RED)

Step 1: Compute daily statistics
  mean   = mean(all 96 prices)
  stddev = stddev(all 96 prices)

Step 2: Classify each slot using all three layers
  FOR each slot i:
    L1[i] = percentile_layer(percentile_rank(i))
    L2[i] = deviation_layer((price[i] - mean) / mean)
    L3[i] = absolute_layer(price[i])

Step 3: Detect uniform days
  IF stddev / mean < 0.10  (coefficient of variation < 10%)
    Mark day as UNIFORM
    FOR each slot i:
      IF L3[i] == GREEN AND L2[i] == GREEN:
        final[i] = GREEN
      ELSE IF L3[i] == RED OR L2[i] == RED:
        final[i] = RED
      ELSE:
        final[i] = AMBER
    RETURN

Step 4: Normal fusion (non-uniform days)
  FOR each slot i:
    votes = [L1[i], L2[i], L3[i]]
    green_count = count(votes == GREEN)
    red_count   = count(votes == RED)

    IF green_count >= 2:    final[i] = GREEN
    ELSE IF red_count >= 2: final[i] = RED
    ELSE:                   final[i] = AMBER

Step 5: Edge smoothing (optional, recommended)
  IF final[i-1] == GREEN AND final[i+1] == GREEN AND final[i] == AMBER:
    final[i] = GREEN      ← "island" smoothing
  IF final[i-1] == RED AND final[i+1] == RED AND final[i] == AMBER:
    final[i] = RED        ← "island" smoothing

RETURN final
```

### Fusion Truth Table (Normal Days)

| L1 (Percentile) | L2 (Deviation) | L3 (Absolute) | → Final |
|---|---|---|---|
| 🟢 | 🟢 | 🟢 | 🟢 GREEN |
| 🟢 | 🟢 | 🟡 | 🟢 GREEN |
| 🟢 | 🟡 | 🟢 | 🟢 GREEN |
| 🟢 | 🟡 | 🟡 | 🟡 AMBER |
| 🟢 | 🔴 | 🟡 | 🔴 RED (2 reds) |
| 🔴 | 🟢 | 🟡 | 🟡 AMBER (split) |
| 🔴 | 🔴 | 🟢 | 🔴 RED |
| 🔴 | 🔴 | 🔴 | 🔴 RED |
| 🟡 | 🟡 | 🟢 | 🟢 GREEN (tie-break: lowest wins) |
| 🟡 | 🟡 | 🔴 | 🔴 RED (tie-break: highest wins) |
| 🟡 | 🟢 | 🟢 | 🟢 GREEN |
| 🟡 | 🔴 | 🔴 | 🔴 RED |

---

## 4. Integration with Recommendations

The traffic-light color directly influences the recommendation engine's appliance-specific logic.

### Recommendation Prioritization by Appliance Type

| Appliance | Strategy Weight: Green Slots | Strategy Weight: Amber Slots | Strategy Weight: Red Slots |
|---|---|---|---|
| **EV Charger** | Preferred (80% weight) | Acceptable if no green available (20%) | Avoid (0%) |
| **Washing Machine** | Preferred (70%) | Acceptable with cost warning (25%) | Avoid (5%) |
| **Dishwasher** | Preferred (70%) | Acceptable with cost warning (25%) | Avoid (5%) |
| **Dryer** | Preferred (60%) | Acceptable (30%) | Avoid (10%) |
| **Pool Pump** | Preferred (75%) | Acceptable (20%) | Avoid (5%) |
| **AC / Heat Pump** | Preferred (50%) | Acceptable (30%) | Avoid if possible (20%) — comfort override |
| **Generic** | Preferred (65%) | Acceptable (25%) | Avoid (10%) |

**Cost Estimation Formula:**

```
estimated_cost(appliance, slot) = price_eur_kwh(slot) × power_kw(appliance) × duration_hours(appliance)
```

**Savings Calculation:**

```
savings = cost(worst_available_slot) - cost(recommended_slot)
```

Where "worst available" is the most expensive slot within the appliance's scheduling constraints.

---

## 5. Handling Edge Cases

### No Tomorrow Prices Yet
- Today's slots use today's data only.
- Tomorrow's recommendation slots default to AMBER until prices are published.
- UI shows "Tomorrow's prices not yet available" banner.

### Extreme Price Events (Negative Prices)
- Negative prices (which occur occasionally in Spain with excess renewable generation) are treated as **deep green**.
- The absolute threshold for GREEN is adjusted: `if price < 0, color = GREEN regardless of other layers`.
- Recommendation engine strongly favors these slots.

### Stale Data (ESIOS Unavailable)
- All slots retain their last-known traffic-light color.
- An `is_stale` flag is set; UI shows a warning banner.
- Recommendation engine degrades to "default schedule" mode: recommend the appliance's preferred window without price optimization.

### User-Modified Thresholds
- Users can override traffic-light colors via alert preferences (e.g., "I consider anything above 0.10 as red for me").
- The override applies **only to notifications**, not to the dashboard classification.
- Dashboard always shows the system-computed color; user preferences filter which alerts fire.

---

## 6. Visualization in the Web Dashboard

```
Hour:   00  01  02  03  04  05  06  07  08  09  10  11  12  13  14  15  16  17  18  19  20  21  22  23
Color:  🟢  🟢  🟢  🟢  🟢  🟢  🟢  🟡  🟡  🟡  🟡  🟡  🔴  🔴  🟡  🟡  🟡  🟡  🔴  🔴  🔴  🟡  🟢  🟢
Price:  04  03  03  03  04  04  05  08  09  10  10  11  18  19  12  11  10  11  20  22  21  09  05  04
        ─────────────────────────────────────────────────────────────────────────────────────────────────
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ cheap overnight ^^^^^^^^^^^^^^^^^^^^^ morning rise ^^^ peak ^^^ cheap night
```

Each cell shows: price in EUR/kWh × 100 (cent/kWh) with background color.

---

## 7. Configuration & Tuning

All thresholds are externalized in `application.yml` for easy adjustment without code changes:

```yaml
wattwise:
  traffic-light:
    # Layer 1: Percentile boundaries
    percentile-green-max: 0.33
    percentile-red-min: 0.67

    # Layer 2: Deviation thresholds (as decimal)
    deviation-green-max: -0.25
    deviation-red-min: 0.25

    # Layer 3: Absolute thresholds (EUR/kWh)
    absolute-green-max: 0.05
    absolute-amber-max: 0.12
    absolute-red-min: 0.20

    # Uniform day detection
    uniform-day-cv-threshold: 0.10

    # Edge smoothing
    smoothing-enabled: true
```

---

## 8. Backtesting & Validation

The methodology will be validated against 12 months of historical PVPC data:

1. **Coverage test:** Every day must produce at least one GREEN and one RED slot (except uniformly priced days).
2. **Savings test:** Following recommendations should yield ≥ 20% savings vs. random scheduling (simulated).
3. **User study (optional):** Compare traffic-light perception with survey data from beta users.
4. **Alert precision:** False positive rate for price-drop alerts < 10%.
