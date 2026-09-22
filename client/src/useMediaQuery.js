import { useEffect, useState } from 'react';

// Tracks a CSS media query from JS so layout decisions stay in one place.
export function useMediaQuery(query) {
  const [matches, setMatches] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia(query).matches : false
  );

  useEffect(() => {
    const mq = window.matchMedia(query);
    const onChange = (e) => setMatches(e.matches);
    setMatches(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [query]);

  return matches;
}

// Phones get the tabbed, one-section-at-a-time layout; wider screens get everything at once.
export const usePhone = () => useMediaQuery('(max-width: 859px)');
