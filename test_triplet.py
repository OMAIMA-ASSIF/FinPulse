from signals.composite_engine import _build_triplet_convergence_signal
from app.db.models.filing import Filing

filing = Filing()
filing.id = 1
filing.company_id = 1

# Test 1 — 3 signaux actifs
signal_values = {
    "rlds": 0.30,
    "forward_pessimism": 0.35,
    "insider_signal": 0.20,
}
result = _build_triplet_convergence_signal(
    filing=filing,
    model_version="test",
    signal_values=signal_values,
)
print("TEST 1 : 3 signaux actifs")
print("signal_value:", result.signal_value)
print("confidence:", result.detail["triplet_confidence"])
print("signals_elevated:", result.detail["triplet_signals_elevated"])
assert result.signal_value == 0.25, f"ECHEC: {result.signal_value}"
print("SUCCES ✓\n")

# Test 2 — confiance < 40%
signal_values["_overall_confidence"] = 0.30
result2 = _build_triplet_convergence_signal(
    filing=filing,
    model_version="test",
    signal_values=signal_values,
)
print("TEST 2 : confiance < 40%")
print("signal_value:", result2.signal_value)
print("confidence:", result2.detail["triplet_confidence"])
assert result2.signal_value == 0.0, f"ECHEC: {result2.signal_value}"
assert result2.detail["triplet_confidence"] == "blocked_low_confidence"
print("SUCCES ✓")