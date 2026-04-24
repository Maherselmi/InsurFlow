import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FeedbackClaimsListComponent } from './feedback-claims-list.component';

describe('FeedbackClaimsListComponent', () => {
  let component: FeedbackClaimsListComponent;
  let fixture: ComponentFixture<FeedbackClaimsListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedbackClaimsListComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(FeedbackClaimsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
