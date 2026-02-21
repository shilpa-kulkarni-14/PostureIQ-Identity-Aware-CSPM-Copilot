import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { AuthRequest, AuthResponse, RegisterRequest } from '../models/auth.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = `${environment.apiUrl}/auth`;
  private tokenKey = 'cspm_token';
  private userKey = 'cspm_user';

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.isAuthenticated());
  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(request: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request).pipe(
      tap((response) => this.storeAuth(response))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, request).pipe(
      tap((response) => this.storeAuth(response))
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
    this.isAuthenticatedSubject.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  getUsername(): string | null {
    const user = localStorage.getItem(this.userKey);
    if (user) {
      return JSON.parse(user).username;
    }
    return null;
  }

  isAuthenticated(): boolean {
    const token = localStorage.getItem(this.tokenKey);
    if (!token) {
      return false;
    }

    try {
      const payload = this.decodeJwtPayload(token);
      if (!payload || !payload.exp) {
        return false;
      }

      const isExpired = payload.exp * 1000 < Date.now();
      if (isExpired) {
        localStorage.removeItem(this.tokenKey);
        localStorage.removeItem(this.userKey);
        return false;
      }

      return true;
    } catch {
      localStorage.removeItem(this.tokenKey);
      localStorage.removeItem(this.userKey);
      return false;
    }
  }

  private decodeJwtPayload(token: string): any {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }

    let payload = parts[1];
    payload = payload.replace(/-/g, '+').replace(/_/g, '/');
    const pad = payload.length % 4;
    if (pad) {
      payload += '='.repeat(4 - pad);
    }

    const decoded = atob(payload);
    return JSON.parse(decoded);
  }

  private storeAuth(response: AuthResponse): void {
    localStorage.setItem(this.tokenKey, response.token);
    localStorage.setItem(
      this.userKey,
      JSON.stringify({ username: response.username, role: response.role })
    );
    this.isAuthenticatedSubject.next(true);
  }
}
