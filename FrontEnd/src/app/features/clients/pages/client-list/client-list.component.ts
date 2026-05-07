import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ClientService, Client } from '../../services/client.service';
import { SidebarComponent } from '../../BackOffice/sidebar/sidebar.component';
import { TopbarComponent } from '../../BackOffice/topbar/topbar.component';

@Component({
  selector: 'app-client-list',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, TopbarComponent],
  templateUrl: './client-list.component.html',
  styleUrls: ['./client-list.component.css']
})
export class ClientListComponent implements OnInit {

  clients: Client[] = [];
  filtered: Client[] = [];
  loading = true;
  searchTerm = '';
  selectedClient: Client | null = null;

  constructor(
    private clientService: ClientService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.loading = true;

    this.clientService.getAllClients().subscribe({
      next: (data) => {
        this.clients = data || [];
        this.filtered = [...this.clients];
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement clients', err);
        this.loading = false;
      }
    });
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();

    if (!term) {
      this.filtered = [...this.clients];
      return;
    }

    this.filtered = this.clients.filter((client) =>
      client.firstName?.toLowerCase().includes(term) ||
      client.lastName?.toLowerCase().includes(term) ||
      client.email?.toLowerCase().includes(term) ||
      client.phone?.toLowerCase().includes(term)
    );
  }

  selectClient(client: Client): void {
    this.selectedClient = this.selectedClient?.id === client.id ? null : client;
  }

  getInitials(client: Client): string {
    return ((client.firstName?.[0] || '') + (client.lastName?.[0] || '')).toUpperCase() || '?';
  }

  getAvatarColor(id: number): string {
    const colors = ['#e8eeff', '#fff3e0', '#e8f5e9', '#fce4ec', '#f3e5f5', '#e0f2f1'];
    return colors[id % colors.length];
  }

  getTextColor(id: number): string {
    const colors = ['#1a2460', '#e65100', '#1b5e20', '#880e4f', '#4a148c', '#004d40'];
    return colors[id % colors.length];
  }

  get totalWithEmail(): number {
    return this.clients.filter(client => !!client.email?.trim()).length;
  }

  get totalWithPhone(): number {
    return this.clients.filter(client => !!client.phone?.trim()).length;
  }

  onNewClaim(): void {
    this.router.navigate(['/dossiers']);
  }
}
