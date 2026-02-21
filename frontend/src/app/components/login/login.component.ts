import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTabsModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  // Login fields
  loginUsername = '';
  loginPassword = '';

  // Register fields
  registerUsername = '';
  registerPassword = '';
  registerEmail = '';

  isLoading = signal(false);
  hidePassword = signal(true);

  constructor(
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  login(): void {
    if (!this.loginUsername || !this.loginPassword) {
      this.snackBar.open('Please fill in all fields', 'Dismiss', { duration: 3000 });
      return;
    }

    this.isLoading.set(true);
    this.authService
      .login({ username: this.loginUsername, password: this.loginPassword })
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.router.navigate(['/']);
        },
        error: (err) => {
          this.isLoading.set(false);
          const message = err.error?.error || 'Login failed. Please try again.';
          this.snackBar.open(message, 'Dismiss', { duration: 5000 });
        },
      });
  }

  register(): void {
    if (!this.registerUsername || !this.registerPassword || !this.registerEmail) {
      this.snackBar.open('Please fill in all fields', 'Dismiss', { duration: 3000 });
      return;
    }

    this.isLoading.set(true);
    this.authService
      .register({
        username: this.registerUsername,
        password: this.registerPassword,
        email: this.registerEmail,
      })
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.router.navigate(['/']);
        },
        error: (err) => {
          this.isLoading.set(false);
          const message = err.error?.error || 'Registration failed. Please try again.';
          this.snackBar.open(message, 'Dismiss', { duration: 5000 });
        },
      });
  }

  togglePasswordVisibility(): void {
    this.hidePassword.update((v) => !v);
  }
}
