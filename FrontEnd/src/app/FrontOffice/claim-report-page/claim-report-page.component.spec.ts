import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimReportPageComponent } from './claim-report-page.component';

describe('ClaimReportPageComponent', () => {
  let component: ClaimReportPageComponent;
  let fixture: ComponentFixture<ClaimReportPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimReportPageComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimReportPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
