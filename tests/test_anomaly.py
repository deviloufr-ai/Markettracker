"""Unit tests for the pure detection functions.

Run with pytest:      python -m pytest
Or standalone:        python tests/test_anomaly.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.detection.anomaly import check_price_move, check_volume_spike  # noqa: E402


def test_price_move_up_triggers():
    history = [(0, 100.0), (30, 100.0), (60, 100.0)]
    trig = check_price_move("X", 104.0, 120, history, threshold_pct=3.0, window_seconds=300)
    assert trig is not None
    assert trig.reason == "price_move"


def test_price_move_down_triggers():
    history = [(0, 100.0), (30, 99.5)]
    trig = check_price_move("X", 95.0, 90, history, threshold_pct=3.0, window_seconds=300)
    assert trig is not None


def test_price_move_below_threshold_is_none():
    history = [(0, 100.0)]
    assert check_price_move("X", 101.0, 60, history, threshold_pct=3.0, window_seconds=300) is None


def test_price_move_no_history_is_none():
    assert check_price_move("X", 101.0, 60, [], threshold_pct=3.0, window_seconds=300) is None


def test_volume_spike_triggers():
    priors = [100.0] * 20
    trig = check_volume_spike("X", 400.0, 100, priors, factor=3.0, min_samples=20, price=10.0)
    assert trig is not None
    assert trig.reason == "volume_spike"


def test_volume_spike_below_factor_is_none():
    priors = [100.0] * 20
    assert check_volume_spike("X", 150.0, 100, priors, factor=3.0, min_samples=20, price=10.0) is None


def test_volume_insufficient_samples_is_none():
    priors = [100.0] * 5
    assert check_volume_spike("X", 400.0, 100, priors, factor=3.0, min_samples=20, price=10.0) is None


def test_volume_none_is_none():
    assert check_volume_spike("X", None, 100, [1.0] * 20, factor=3.0, min_samples=20, price=10.0) is None


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print(f"  ok  {t.__name__}")
    print(f"\n{len(tests)} test(s) passed.")
