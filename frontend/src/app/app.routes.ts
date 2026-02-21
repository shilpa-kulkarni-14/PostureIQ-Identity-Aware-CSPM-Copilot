import { Routes } from '@angular/router';
import { ScannerComponent } from './components/scanner/scanner.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { PostureIqComponent } from './components/postureiq/postureiq.component';
import { LoginComponent } from './components/login/login.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'Login - CSPM Scanner' },
  { path: '', component: ScannerComponent, canActivate: [authGuard], title: 'Security Scanner - CSPM Scanner' },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard], title: 'Compliance Dashboard - CSPM Scanner' },
  { path: 'postureiq', component: PostureIqComponent, canActivate: [authGuard], title: 'PostureIQ - CSPM Scanner' },
  { path: '**', redirectTo: '' }
];
