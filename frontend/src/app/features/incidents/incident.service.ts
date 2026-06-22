import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateIncidentRequest,
  CreatedIncident,
  IncidentDetails,
  IncidentPage,
  ResolveIncidentRequest
} from './incident.models';

@Injectable({ providedIn: 'root' })
export class IncidentService {
  private readonly http = inject(HttpClient);

  getIncidents(page: number, size: number, sort: string): Observable<IncidentPage> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);

    return this.http.get<IncidentPage>('/api/incidents', { params });
  }

  createIncident(request: CreateIncidentRequest): Observable<CreatedIncident> {
    return this.http.post<CreatedIncident>('/api/incidents', request);
  }

  getIncidentById(id: number): Observable<IncidentDetails> {
    return this.http.get<IncidentDetails>(`/api/incidents/${id}`);
  }

  assignToMe(id: number): Observable<IncidentDetails> {
    return this.http.post<IncidentDetails>(`/api/incidents/${id}/assign-to-me`, null);
  }

  analyzeIncident(id: number): Observable<IncidentDetails> {
    return this.http.post<IncidentDetails>(`/api/incidents/${id}/analyze`, null);
  }

  resolveIncident(
    id: number,
    request: ResolveIncidentRequest
  ): Observable<IncidentDetails> {
    return this.http.post<IncidentDetails>(`/api/incidents/${id}/resolve`, request);
  }
}
