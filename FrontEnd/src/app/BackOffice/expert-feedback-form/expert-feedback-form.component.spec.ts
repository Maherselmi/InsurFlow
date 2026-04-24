import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExpertFeedbackFormComponent } from './expert-feedback-form.component';

describe('ExpertFeedbackFormComponent', () => {
  let component: ExpertFeedbackFormComponent;
  let fixture: ComponentFixture<ExpertFeedbackFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExpertFeedbackFormComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ExpertFeedbackFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
