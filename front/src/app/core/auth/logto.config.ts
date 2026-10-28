import { InjectionToken } from '@angular/core';

export interface LogtoSettings {
  endpoint: string;
  appId: string;
}

export const LOGTO_SETTINGS = new InjectionToken<LogtoSettings>('LOGTO_SETTINGS');
