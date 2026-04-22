import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConsultationDecisionsComponent } from './consultation-decisions.component';

describe('ConsultationDecisionsComponent', () => {
  let component: ConsultationDecisionsComponent;
  let fixture: ComponentFixture<ConsultationDecisionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConsultationDecisionsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ConsultationDecisionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
