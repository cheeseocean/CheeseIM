function installStoragePolyfill(): void {
  if (typeof window === 'undefined') {
    return;
  }

  const candidate = window.localStorage as Partial<Storage> | undefined;
  if (
    candidate != null &&
    typeof candidate.getItem === 'function' &&
    typeof candidate.setItem === 'function' &&
    typeof candidate.removeItem === 'function' &&
    typeof candidate.clear === 'function'
  ) {
    return;
  }

  const store = new Map<string, string>();
  const polyfill: Storage = {
    get length() {
      return store.size;
    },
    clear() {
      store.clear();
    },
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null;
    },
    key(index: number) {
      return Array.from(store.keys())[index] ?? null;
    },
    removeItem(key: string) {
      store.delete(key);
    },
    setItem(key: string, value: string) {
      store.set(String(key), String(value));
    },
  };

  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: polyfill,
  });
}

installStoragePolyfill();
