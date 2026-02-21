import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { DashboardService, DashboardStats } from '../../services/dashboard.service';
import { PostureIqService } from '../../services/postureiq.service';
import { HighRiskIdentity } from '../../models/finding.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    BaseChartDirective,
    RouterLink
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  loading = signal(true);
  stats = signal<DashboardStats | null>(null);
  error = signal<string | null>(null);
  highRiskIdentities = signal<HighRiskIdentity[]>([]);
  identityColumns = ['name', 'type', 'riskScore'];

  // Doughnut chart - category breakdown
  categoryChartData: ChartData<'doughnut', number[], string> = {
    labels: ['Config', 'IAM', 'Correlated'],
    datasets: [{
      data: [0, 0, 0],
      backgroundColor: ['#388e3c', '#3949ab', '#c62828'],
      borderWidth: 2,
      borderColor: '#ffffff'
    }]
  };

  categoryChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true } }
    }
  };

  // Pie chart - severity distribution
  pieChartData: ChartData<'doughnut', number[], string> = {
    labels: ['High', 'Medium', 'Low'],
    datasets: [{
      data: [0, 0, 0],
      backgroundColor: ['#d32f2f', '#f57c00', '#1976d2'],
      borderWidth: 2,
      borderColor: '#ffffff'
    }]
  };

  pieChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true } }
    }
  };

  // Bar chart - findings by resource type
  barChartData: ChartData<'bar', number[], string> = {
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: ['#1976d2', '#7b1fa2', '#388e3c', '#f57c00'],
      borderRadius: 6
    }]
  };

  barChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false }
    },
    scales: {
      y: { beginAtZero: true, ticks: { stepSize: 1 } }
    }
  };

  // Line chart - scan history trends
  lineChartData: ChartData<'line', number[], string> = {
    labels: [],
    datasets: [
      { data: [], label: 'Total', borderColor: '#616161', backgroundColor: 'rgba(97,97,97,0.1)', fill: true, tension: 0.3 },
      { data: [], label: 'High', borderColor: '#d32f2f', backgroundColor: 'rgba(211,47,47,0.1)', fill: false, tension: 0.3 },
      { data: [], label: 'Medium', borderColor: '#f57c00', backgroundColor: 'rgba(245,124,0,0.1)', fill: false, tension: 0.3 },
      { data: [], label: 'Low', borderColor: '#1976d2', backgroundColor: 'rgba(25,118,210,0.1)', fill: false, tension: 0.3 }
    ]
  };

  lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true } }
    },
    scales: {
      y: { beginAtZero: true, ticks: { stepSize: 1 } },
      x: { ticks: { maxRotation: 45 } }
    }
  };

  constructor(
    private dashboardService: DashboardService,
    private postureIqService: PostureIqService
  ) {}

  ngOnInit(): void {
    this.loadStats();
    this.loadHighRiskIdentities();
  }

  loadStats(): void {
    this.loading.set(true);
    this.error.set(null);

    this.dashboardService.getStats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.updateCharts(stats);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load dashboard data. Ensure the backend is running.');
        this.loading.set(false);
        console.error('Dashboard error:', err);
      }
    });
  }

  loadHighRiskIdentities(): void {
    this.postureIqService.getHighRiskIdentities().subscribe({
      next: (identities) => this.highRiskIdentities.set(identities.slice(0, 5)),
      error: (err) => console.error('Error loading high-risk identities:', err)
    });
  }

  private updateCharts(stats: DashboardStats): void {
    // Pie chart
    this.pieChartData = {
      ...this.pieChartData,
      datasets: [{
        ...this.pieChartData.datasets[0],
        data: [
          stats.severityDistribution.HIGH,
          stats.severityDistribution.MEDIUM,
          stats.severityDistribution.LOW
        ]
      }]
    };

    // Bar chart
    const resourceTypes = Object.keys(stats.findingsByResourceType);
    const resourceCounts = Object.values(stats.findingsByResourceType);
    this.barChartData = {
      labels: resourceTypes,
      datasets: [{
        ...this.barChartData.datasets[0],
        data: resourceCounts
      }]
    };

    // Line chart
    const labels = stats.scanHistory.map((entry, i) => {
      const date = new Date(entry.timestamp);
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    });

    this.lineChartData = {
      labels,
      datasets: [
        { ...this.lineChartData.datasets[0], data: stats.scanHistory.map(e => e.totalFindings) },
        { ...this.lineChartData.datasets[1], data: stats.scanHistory.map(e => e.highSeverity) },
        { ...this.lineChartData.datasets[2], data: stats.scanHistory.map(e => e.mediumSeverity) },
        { ...this.lineChartData.datasets[3], data: stats.scanHistory.map(e => e.lowSeverity) }
      ]
    };
  }
}
