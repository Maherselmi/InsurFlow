import { Routes } from '@angular/router';

import {ConsultationDecisionsComponent} from "./FrontOffice/consultation-decisions/consultation-decisions.component";
import {InsuranceHomeComponent} from "./FrontOffice/insurance-home/insurance-home.component";
import {LoginComponent} from "./FrontOffice/login/login.component";
import {ClientSpaceComponent} from "./FrontOffice/client-space/client-space.component";
import {ClaimsHomeComponent} from "./FrontOffice/claims-home/claims-home.component";
import {ClaimStep3SanteComponent} from "./FrontOffice/Sante/claim-step3-sante/claim-step3-sante.component";
import {ClaimStep2SanteComponent} from "./FrontOffice/Sante/claim-step2-sante/claim-step2-sante.component";
import {ClaimStep1SanteComponent} from "./FrontOffice/Sante/claim-step1-sante/claim-step1-sante.component";
import {
  ClaimStep3HabitationComponent
} from "./FrontOffice/Habitation/claim-step3-habitation/claim-step3-habitation.component";
import {
  ClaimStep2HabitationComponent
} from "./FrontOffice/Habitation/claim-step2-habitation/claim-step2-habitation.component";
import {
  ClaimStep1HabitationComponent
} from "./FrontOffice/Habitation/claim-step1-habitation/claim-step1-habitation.component";
import {ClaimStep1Component} from "./FrontOffice/Auto/claim-step1/claim-step1.component";
import {ClaimStep2Component} from "./FrontOffice/Auto/claim-step2/claim-step2.component";
import {ClaimStep3Component} from "./FrontOffice/Auto/claim-step3/claim-step3.component";
import {ClaimReportPageComponent} from "./FrontOffice/claim-report-page/claim-report-page.component";
import {DossierSinistreComponent} from "./BackOffice/dossier-sinistre/dossier-sinistre.component";
import {AgentResultListComponent} from "./BackOffice/agent-result-list/agent-result-list.component";
import {PolicyListComponent} from "./BackOffice/policy-list/policy-list.component";
import {ClientListComponent} from "./BackOffice/client-list/client-list.component";
import {DashboardComponent} from "./BackOffice/dashboard/dashboard.component";
import {PolicesComponent} from "./FrontOffice/polices/polices.component";
import {ClaimValidationComponent} from "./BackOffice/claim-validation/claim-validation.component";
import {ParametresIaComponent} from "./BackOffice/parametres-ia/parametres-ia.component";
import {FeedbackClaimsListComponent} from "./BackOffice/feedback-claims-list/feedback-claims-list.component";
import {ExpertFeedbackFormComponent} from "./BackOffice/expert-feedback-form/expert-feedback-form.component";
import {ExpertSpaceComponent} from "./BackOffice/expert-space/expert-space.component";

export const routes: Routes = [
  { path: 'PolicesList', component: PolicesComponent },
  { path: 'Claim_Home', component: ClaimsHomeComponent },

  { path: 'ia_param', component: ParametresIaComponent },
  { path: 'expert-space', component:ExpertSpaceComponent },

  { path: 'feedback-claims', component: FeedbackClaimsListComponent },
  { path: 'expert-feedback/:claimId', component: ExpertFeedbackFormComponent },
  { path: 'Client_Space', component: ClientSpaceComponent },
  {path: 'claim-report/:id', component: ClaimReportPageComponent},

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
