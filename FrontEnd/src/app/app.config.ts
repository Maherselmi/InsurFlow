import { ApplicationConfig } from '@angular/core';
import {
  provideRouter,
  withInMemoryScrolling
} from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(
      routes,
      withInMemoryScrolling({
        scrollPositionRestoration: 'top',
        anchorScrolling: 'enabled'
      })
    ),

    provideCharts(withDefaultRegisterables()),
    provideClientHydration(),
    provideHttpClient()
  ]
};
