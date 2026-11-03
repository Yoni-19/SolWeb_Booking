/**
 * Paginated response from the backend. Shape fixed by Spring Data's VIA_DTO mode
 * (see backend `config/WebConfig`): `content` plus a `page` metadata object.
 */
export interface Pagina<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}
