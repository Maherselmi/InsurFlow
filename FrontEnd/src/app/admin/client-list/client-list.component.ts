import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../components/sidebar/sidebar.component';
import { TopbarComponent } from '../../components/topbar/topbar.component';
import { ClientService, Client } from '../../services/client.service';

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

  constructor(private clientService: ClientService) {}

  ngOnInit(): void {
    this.clientService.getAllClients().subscribe({
      next: (data) => {
        this.clients = data;
        this.filtered = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur', err);
        this.loading = false;
      }
    });
  }

  onSearch(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) { this.filtered = this.clients; return; }
    this.filtered = this.clients.filter(c =>
      c.firstName?.toLowerCase().includes(term) ||
      c.lastName?.toLowerCase().includes(term) ||
      c.email?.toLowerCase().includes(term) ||
      c.phone?.includes(term)
    );
  }

  getInitials(c: Client): string {
    return ((c.firstName?.[0] || '') + (c.lastName?.[0] || '')).toUpperCase() || '?';
  }

  selectClient(c: Client): void {
    this.selectedClient = this.selectedClient?.id === c.id ? null : c;
  }

  getAvatarColor(id: number): string {
    const colors = ['#e8eeff','#fff3e0','#e8f5e9','#fce4ec','#f3e5f5','#e0f2f1'];
    return colors[id % colors.length];
  }

  getTextColor(id: number): string {
    const colors = ['#1a2460','#e65100','#1b5e20','#880e4f','#4a148c','#004d40'];
    return colors[id % colors.length];
  }
}
