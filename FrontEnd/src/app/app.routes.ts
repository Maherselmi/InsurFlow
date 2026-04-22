import { Routes } from '@angular/router';
import { ClaimStep1Component } from './claim/claim-step1/claim-step1.component';
import { ClaimStep2Component } from './claim/claim-step2/claim-step2.component';
import { ClaimStep3Component } from './claim/claim-step3/claim-step3.component';
import { DashboardComponent } from "./dashboard/dashboard.component";
import {DossierSinistreComponent} from "./admin/dossier-sinistre/dossier-sinistre.component";
import {ClientListComponent} from "./admin/client-list/client-list.component";
import {PolicyListComponent} from "./admin/policy-list/policy-list.component";
import {AgentResultListComponent} from "./admin/agent-result-list/agent-result-list.component";
import {HomeComponent} from "./pages/home/home.component";
import {ClaimStep1SanteComponent} from "./claim/Sante/claim-step1-sante/claim-step1-sante.component";
import {ClaimStep2SanteComponent} from "./claim/Sante/claim-step2-sante/claim-step2-sante.component";
import {ClaimStep3SanteComponent} from "./claim/Sante/claim-step3-sante/claim-step3-sante.component";
import {
  ClaimStep1HabitationComponent
} from "./claim/Habitation/claim-step1-habitation/claim-step1-habitation.component";
import {
  ClaimStep2HabitationComponent
} from "./claim/Habitation/claim-step2-habitation/claim-step2-habitation.component";
import {
  ClaimStep3HabitationComponent
} from "./claim/Habitation/claim-step3-habitation/claim-step3-habitation.component";
import {ConsultationDecisionsComponent} from "./client/consultation-decisions/consultation-decisions.component";
import {ClaimValidationComponent} from "./claim/claim-validation/claim-validation.component";
import {InsuranceHomeComponent} from "./FrontOffice/insurance-home/insurance-home.component";
import {LoginComponent} from "./FrontOffice/login/login.component";
import {PolicesComponent} from "./client/polices/polices.component";
import {ClientSpaceComponent} from "./FrontOffice/client-space/client-space.component";
import {ClaimsHomeComponent} from "./FrontOffice/claims-home/claims-home.component";

export const routes: Routes = [
  { path: 'PolicesList', component: PolicesComponent },
  { path: 'Claim_Home', component: ClaimsHomeComponent },



  { path: 'Client_Space', component: ClientSpaceComponent },


  { path: 'login', component: LoginComponent },
  { path: 'Validation', component: ClaimValidationComponent },
  { path: 'Consulter', component: ConsultationDecisionsComponent },

  { path: 'Habitation/step3', component: ClaimStep3HabitationComponent },
  { path: 'Habitation/step2', component: ClaimStep2HabitationComponent },
  { path: 'Habitation/step1', component: ClaimStep1HabitationComponent },

  { path: 'Sante/step3', component: ClaimStep3SanteComponent },
  { path: 'Sante/step2', component: ClaimStep2SanteComponent },
  { path: 'Sante/step1', component: ClaimStep1SanteComponent },

  { path: 'clients', component: ClientListComponent },
  { path: 'polices', component: PolicyListComponent },
  { path: 'agents', component: AgentResultListComponent },

  { path: '', component: InsuranceHomeComponent },
  { path: 'sinistre', component: HomeComponent },

  { path: 'dashboard', component: DashboardComponent },
  { path: 'dossiers', component: DossierSinistreComponent },

  {
    path: 'claim',
    children: [
      { path: 'step1', component: ClaimStep1Component },
      { path: 'step2', component: ClaimStep2Component },
      { path: 'step3', component: ClaimStep3Component }
    ]
  },

  { path: '**', redirectTo: '' }
];
