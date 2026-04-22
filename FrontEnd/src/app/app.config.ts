import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';  // AJOUTEZ CET IMPORT

import { routes } from './app.routes';
import {provideCharts, withDefaultRegisterables} from "ng2-charts";

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideCharts(withDefaultRegisterables()),

    provideClientHydration(),
    provideHttpClient()  // AJOUTEZ CE PROVIDER
  ]
};
