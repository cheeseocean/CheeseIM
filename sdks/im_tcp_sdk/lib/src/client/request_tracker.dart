import 'dart:async';

final class RequestTracker<T> {
  RequestTracker({required this.timeout});

  final Duration timeout;
  final Map<String, Completer<T>> _pending = <String, Completer<T>>{};
  final Map<String, Timer> _timers = <String, Timer>{};

  Future<T> track(String operationId) {
    final completer = Completer<T>();
    _pending[operationId] = completer;
    _timers[operationId] = Timer(timeout, () {
      if (!completer.isCompleted) {
        completer.completeError(TimeoutException('Request timed out', timeout));
      }
      _pending.remove(operationId);
      _timers.remove(operationId);
    });
    return completer.future;
  }

  void resolve(String operationId, T value) {
    final completer = _pending.remove(operationId);
    _timers.remove(operationId)?.cancel();
    completer?.complete(value);
  }

  void fail(String operationId, Object error, [StackTrace? stackTrace]) {
    final completer = _pending.remove(operationId);
    _timers.remove(operationId)?.cancel();
    completer?.completeError(error, stackTrace);
  }

  void dispose() {
    for (final timer in _timers.values) {
      timer.cancel();
    }
    _timers.clear();
    _pending.clear();
  }
}
