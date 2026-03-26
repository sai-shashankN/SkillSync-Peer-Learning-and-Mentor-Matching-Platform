import { useState } from 'react';

export function usePagination(initialPage = 0, initialSize = 10) {
  const [page, setPage] = useState(initialPage);
  const [size, setSize] = useState(initialSize);

  const nextPage = () => setPage((current) => current + 1);
  const prevPage = () => setPage((current) => Math.max(0, current - 1));

  return { page, size, setPage, setSize, nextPage, prevPage };
}
