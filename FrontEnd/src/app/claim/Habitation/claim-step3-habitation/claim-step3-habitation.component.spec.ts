import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep3HabitationComponent } from './claim-step3-habitation.component';

describe('ClaimStep3HabitationComponent', () => {
  let component: ClaimStep3HabitationComponent;
  let fixture: ComponentFixture<ClaimStep3HabitationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep3HabitationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep3HabitationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
