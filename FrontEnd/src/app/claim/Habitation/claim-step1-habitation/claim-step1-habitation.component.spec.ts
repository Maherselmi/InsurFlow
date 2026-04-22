import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep1HabitationComponent } from './claim-step1-habitation.component';

describe('ClaimStep1HabitationComponent', () => {
  let component: ClaimStep1HabitationComponent;
  let fixture: ComponentFixture<ClaimStep1HabitationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep1HabitationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep1HabitationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
